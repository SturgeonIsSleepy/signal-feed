import type { RawItem, SourceAdapter } from './types.ts';

const UA = 'SignalFeed/0.1 (personal non-commercial reader)';
const gdelt = 'https://api.gdeltproject.org/api/v2/doc/doc';

export function stripHtml(input: string): string {
  return input.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '')
    .replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, '')
    .replace(/<[^>]*>/g, ' ').replace(/&nbsp;|&#160;/gi, ' ')
    .replace(/&amp;/gi, '&').replace(/&quot;/gi, '"').replace(/&#39;|&apos;/gi, "'")
    .replace(/&#(x[0-9a-f]+|\d+);/gi, (_, value: string) => String.fromCodePoint(
      value[0].toLowerCase() === 'x' ? parseInt(value.slice(1), 16) : parseInt(value, 10)))
    .replace(/&lt;/gi, '<').replace(/&gt;/gi, '>').replace(/\s+/g, ' ').trim();
}
function xmlTag(fragment: string, tag: string): string {
  const match = fragment.match(new RegExp(`<${tag}(?:\\s[^>]*)?>([\\s\\S]*?)<\\/${tag}>`, 'i'));
  return stripHtml(match?.[1]?.replace(/^<!\[CDATA\[|\]\]>$/g, '') ?? '');
}
export function rssItems(xml: string): { title: string; link: string; publishedAt: number; body: string }[] {
  return [...xml.matchAll(/<item(?:\s[^>]*)?>([\s\S]*?)<\/item>/gi)].map(match => {
    const raw = match[1];
    return {
      title: xmlTag(raw, 'title'), link: xmlTag(raw, 'link'),
      publishedAt: Date.parse(xmlTag(raw, 'pubDate') || xmlTag(raw, 'dc:date')) || 0,
      body: stripHtml(xmlTag(raw, 'content:encoded') || xmlTag(raw, 'description'))
    };
  }).filter(item => item.title && item.link.startsWith('https://') && item.publishedAt > 0);
}
async function getText(url: string): Promise<string> {
  const response = await fetch(url, { headers: { 'User-Agent': UA, Accept: 'application/rss+xml, application/xml, text/html, application/json' }, signal: AbortSignal.timeout(12_000) });
  if (!response.ok) throw Error(`${response.status} ${new URL(url).hostname}`);
  return response.text();
}
function gdeltDate(input: string): number {
  const compact = input.replace(/\D/g, '');
  if (compact.length < 14) return Date.parse(input) || 0;
  return Date.parse(`${compact.slice(0,4)}-${compact.slice(4,6)}-${compact.slice(6,8)}T${compact.slice(8,10)}:${compact.slice(10,12)}:${compact.slice(12,14)}Z`) || 0;
}
export async function gdeltItems(query: string): Promise<{ title: string; url: string; domain: string; publishedAt: number }[]> {
  const url = new URL(gdelt);
  url.searchParams.set('query', query);
  url.searchParams.set('mode', 'artlist');
  url.searchParams.set('format', 'json');
  url.searchParams.set('maxrecords', '40');
  url.searchParams.set('timespan', '1d');
  const payload = JSON.parse(await getText(url.toString())) as { articles?: { title?: string; url?: string; domain?: string; seendate?: string }[] };
  return (payload.articles ?? []).flatMap(article => {
    if (!article.title || !article.url?.startsWith('https://')) return [];
    const domain = new URL(article.url).hostname.toLowerCase();
    return [{ title: stripHtml(article.title), url: article.url, domain, publishedAt: gdeltDate(article.seendate ?? '') }];
  }).filter(item => item.publishedAt > 0);
}
const officialDomain = (domain: string, expected: string) => domain === expected || domain.endsWith(`.${expected}`);
const story = (item: { title: string; url: string; domain: string; publishedAt: number }, topicId: RawItem['topicId'], sourceName: string, importance: number, breaking = false): RawItem => ({
  externalId: item.url, title: item.title, body: item.title, originalUrl: item.url,
  publishedAt: item.publishedAt, domain: item.domain, sourceName,
  confidence: 'OFFICIAL', topicId, importance, breaking
});

export function isJiangnanK80Ultra(title: string, page: string): boolean {
  const heading = title.replace(/\s+/g, '').toLowerCase();
  return /江南烟雨断桥殇/.test(stripHtml(page)) && /(?:redmi)?k80(?:至尊版|至尊|ultra|u)(?!\w)/i.test(heading)
    && /官改|改包/.test(heading);
}
export function isWuxingF1(text: string): boolean {
  return /(?:\bF1\b|一级方程式|Formula\s*(?:1|One))/i.test(text) && !/赛艇|摩托艇/i.test(text);
}
export function isWuxingPost(title: string, url: string): boolean {
  return /^五星体育\s/.test(title) && isWuxingF1(title)
    && /^https:\/\/(?:www\.)?sina\.cn\/news\/detail\/\d+\.html$/.test(url);
}

export function isOpenAiProductUpdate(title: string): boolean {
  return /chatgpt|codex|\bgpt[-\s]?\d|\bmodels?\b|release notes|pricing|usage limits|rate limits/i.test(title)
    && !/\bhow\b.*\b(?:uses?|with)\b|case study|customer story/i.test(title);
}

export function f1ArticleCards(html: string): { title: string; url: string }[] {
  return [...html.matchAll(/<a\b[^>]*href="(\/en\/latest\/article\/[^\"]+)"[^>]*>([\s\S]*?)<\/a>/gi)]
    .map(match => ({ title: stripHtml(match[2]), url: new URL(match[1], 'https://www.formula1.com').toString() }))
    .filter(item => item.title && !/\bF2\b|\bF3\b|F1 Academy/i.test(item.title))
    .filter((item, index, all) => all.findIndex(other => other.url === item.url) === index);
}

export function fiaDocuments(html: string): { title: string; url: string; publishedAt: number }[] {
  return [...html.matchAll(/<li\b[^>]*class="[^"]*document-row[^"]*"[^>]*>([\s\S]*?)<\/li>/gi)].flatMap(match => {
    const row = match[1];
    const href = row.match(/<a\b[^>]*href="([^"]+\.pdf)"/i)?.[1];
    const title = stripHtml(row.match(/<div\b[^>]*class="title"[^>]*>([\s\S]*?)<\/div>/i)?.[1] ?? '');
    const date = row.match(/date-display-single[^>]*>(\d{2})\.(\d{2})\.(\d{2})\s+(\d{2}):(\d{2})/i);
    if (!href || !date || !/infringement|decision|penalty|regulation|race director notes/i.test(title)) return [];
    const [, day, month, year, hour, minute] = date;
    const offset = Number(month) >= 4 && Number(month) <= 10 ? '+02:00' : '+01:00';
    const publishedAt = Date.parse(`20${year}-${month}-${day}T${hour}:${minute}:00${offset}`);
    return publishedAt ? [{ title, url: new URL(href, 'https://www.fia.com').toString(), publishedAt }] : [];
  }).slice(0, 20);
}

export function verifiedXPost(html: string, url: string, mirrorTitle: string): number | null {
  const originalUrl = html.match(/<meta property="og:url" content="([^"]+)"/i)?.[1];
  const originalText = stripHtml(html.match(/<meta property="og:description" content="([\s\S]*?)"/i)?.[1] ?? '');
  const published = html.match(/<meta property="article:published_time" content="([^"]+)"/i)?.[1];
  if (originalUrl !== url || !originalText.startsWith(mirrorTitle.slice(0, 50))) return null;
  const publishedAt = Date.parse(published ?? '');
  return publishedAt || null;
}

export function pageDescription(html: string): string {
  const raw = html.match(/<meta\s+name="description"\s+content="([^"]+)"/i)?.[1]
    ?? html.match(/<meta\s+property="og:description"\s+content="([^"]+)"/i)?.[1]
    ?? '';
  return stripHtml(raw);
}

export function f1ArticleBody(html: string, title: string): string {
  const chunks: string[] = [];
  for (const script of html.matchAll(/self\.__next_f\.push\((\[.*?\])\)<\/script>/gs)) {
    try {
      const payload = JSON.parse(script[1]);
      if (typeof payload[1] === 'string') chunks.push(payload[1]);
    } catch { /* Ignore non-data scripts. */ }
  }
  const references = new Map<string, string>();
  const stream = chunks.join('');
  for (const match of stream.matchAll(/([a-f0-9]+):T([a-f0-9]+),/g)) {
    const bytes = new TextEncoder().encode(stream.slice(match.index + match[0].length));
    references.set(match[1], new TextDecoder().decode(bytes.slice(0, parseInt(match[2], 16))));
  }
  for (const script of html.matchAll(/self\.__next_f\.push\((\[.*?\])\)<\/script>/gs)) {
    try {
      const payload = JSON.parse(script[1]) as [number, string];
      if (typeof payload[1] !== 'string' || !payload[1].includes('fullContent')) continue;
      const root = JSON.parse(payload[1].slice(payload[1].indexOf(':') + 1)) as unknown;
      let content: unknown;
      const find = (value: unknown): void => {
        if (!value || typeof value !== 'object' || content) return;
        const node = value as Record<string, unknown>;
        if (node.isProtected === false && node.fullContent) { content = node.fullContent; return; }
        Object.values(node).forEach(find);
      };
      find(root);
      if (!content) continue;
      const paragraphs: string[] = [];
      const collect = (value: unknown): void => {
        if (!value || typeof value !== 'object') return;
        const node = value as Record<string, unknown>;
        if (typeof node.text === 'string') {
          const text = node.text.startsWith('$') ? references.get(node.text.slice(1)) : node.text;
          if (text) paragraphs.push(text.replace(/\[([^\]]+)\]\(https?:\/\/[^)]+\)/g, '$1').replace(/\*\*|__/g, '').trim());
        }
        Object.values(node).forEach(collect);
      };
      collect(content);
      if (paragraphs.length) return `${title}\n\n${paragraphs.join('\n\n')}`;
    } catch { /* Fall back to the public page description. */ }
  }
  const description = pageDescription(html);
  return description && description !== title ? `${title}\n\n${description}` : title;
}

export const officialF1: SourceAdapter = {
  id: 'f1-official', accountId: 'f1', homeUrl: 'https://www.formula1.com/',
  async fetchItems() {
    const cards = f1ArticleCards(await getText('https://www.formula1.com/en/latest')).slice(0, 8);
    const found = await Promise.all(cards.map(async card => {
      try {
        const html = await getText(card.url);
        const date = html.match(/datePublished"\s*:\s*"([^"]+)"/i)?.[1];
        const publishedAt = Date.parse(date ?? '');
        if (!publishedAt) return null;
        const news = story({ title: card.title, url: card.url, domain: 'formula1.com', publishedAt },
          'F1', 'Formula 1', /breaking|confirmed|official/i.test(card.title) ? 85 : 55, /\bbreaking\b/i.test(card.title));
        news.body = f1ArticleBody(html, card.title);
        return news;
      } catch { return null; }
    }));
    return found.filter((item): item is RawItem => item !== null);
  }
};

export const fia: SourceAdapter = {
  id: 'fia-documents', accountId: 'fia', homeUrl: 'https://www.fia.com/documents',
  async fetchItems() {
    const html = await getText('https://www.fia.com/documents/championships/fia-formula-one-world-championship-14/season/season-2026-2072');
    return fiaDocuments(html).map(item => story({ ...item, domain: 'fia.com' }, 'F1', 'FIA', 75,
      /decision|penalty/i.test(item.title)));
  }
};

export const openai: SourceAdapter = {
  id: 'openai-official', accountId: 'openai', homeUrl: 'https://openai.com/news/',
  async fetchItems() {
    const rss = rssItems(await getText('https://openai.com/news/rss.xml'));
    const news: RawItem[] = rss.filter(item => isOpenAiProductUpdate(item.title) && item.publishedAt > Date.now() - 30 * 24 * 60 * 60_000)
      .map(item => ({ externalId: item.link, title: item.title,
        body: item.body && item.body !== item.title ? `${item.title}\n\n${item.body}` : item.title,
        originalUrl: item.link, publishedAt: item.publishedAt, domain: 'openai.com',
        sourceName: 'OpenAI', confidence: 'OFFICIAL' as const, topicId: 'AI' as const,
        importance: 75, breaking: /price|pricing|limit|launch|release/i.test(item.title) }));
    try {
      const help = await gdeltItems('domain:help.openai.com (ChatGPT OR Codex OR model)');
      news.push(...help.filter(item => officialDomain(item.domain, 'help.openai.com'))
        .map(item => story(item, 'AI', 'OpenAI Help Center', 65, /price|limit|release|changelog/i.test(item.title))));
    } catch { /* The official RSS can still succeed while the secondary index is unavailable. */ }
    return news;
  }
};

export const wuxing: SourceAdapter = {
  id: 'wuxing-weibo', accountId: 'wuxing', homeUrl: 'https://weibo.com/wuxingtiyu',
  async fetchItems() {
    const html = await getText('https://www.sina.cn/media/1797246290');
    const matches = [...html.matchAll(/<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi)].slice(0, 300);
    if (!matches.length) throw Error('Sina page structure not recognized');
    return matches.flatMap(match => {
      const title = stripHtml(match[2]);
      const href = new URL(match[1], 'https://www.sina.cn').toString();
      if (!isWuxingPost(title, href)) return [];
      const nearby = html.slice(Math.max(0, match.index - 200), match.index + match[0].length + 200);
      const date = nearby.match(/20\d{2}[-/.年]\d{1,2}[-/.月]\d{1,2}(?:日)?\s*\d{1,2}:\d{2}/)?.[0];
      const publishedAt = date ? Date.parse(date.replace(/[年月]/g, '-').replace('日', '').replace(/\//g, '-').replace(' ', 'T') + ':00+08:00') : 0;
      if (!publishedAt) return [];
      return [{ externalId: href, title, body: title, originalUrl: href, publishedAt,
        domain: new URL(href).hostname, sourceName: '五星体育', confidence: 'HIGH' as const,
        topicId: 'F1' as const, importance: 45, breaking: false }];
    });
  }
};

export const jiangnan: SourceAdapter = {
  id: 'jiangnan-coolapk', accountId: 'jiangnan', homeUrl: 'https://www.coolapk.com/',
  async fetchItems() {
    const url = new URL('https://www.bing.com/search');
    url.searchParams.set('q', 'site:coolapk1s.com/feed/ "江南烟雨断桥殇" "K80 至尊" 官改');
    url.searchParams.set('format', 'rss');
    const search = rssItems(await getText(url.toString())).slice(0, 10);
    const out: RawItem[] = [];
    for (const result of search) {
      if (!/^https:\/\/(www\.)?coolapk1s\.com\/feed\/\d+/.test(result.link)) continue;
      const page = await getText(result.link);
      if (!isJiangnanK80Ultra(result.title, page)) continue;
      out.push({ externalId: result.link, title: result.title, body: result.title,
        originalUrl: result.link, publishedAt: result.publishedAt,
        domain: 'coolapk1s.com', sourceName: '江南烟雨断桥殇（酷安镜像）',
        confidence: 'HIGH', topicId: '玩机', importance: 65, breaking: false });
    }
    return out;
  }
};

export const radar: SourceAdapter = {
  id: 'tibo-mirror', accountId: 'radar', homeUrl: 'https://x.com/thsottiaux',
  async fetchItems() {
    const feed = rssItems(await getText('https://x.noodl3.net/thsottiaux/rss')).slice(0, 12);
    const mapped: RawItem[] = feed.flatMap(item => {
      const id = item.link.match(/\/status\/(\d+)/)?.[1];
      if (!id || !/chatgpt|codex|openai|gpt|model|limit|usage|price/i.test(`${item.title} ${item.body}`)) return [];
      const original = `https://x.com/thsottiaux/status/${id}`;
      return [{ externalId: id, title: item.title, body: item.body || item.title,
        originalUrl: original, publishedAt: item.publishedAt, domain: 'x.com',
        sourceName: 'Tibo 公开发言（镜像索引）', confidence: 'UNCONFIRMED' as const,
        topicId: 'AI' as const, importance: 55, breaking: false }];
    });
    await Promise.all(mapped.map(async item => {
      try {
        const check = await fetch(item.originalUrl, { headers: { 'User-Agent': 'Mozilla/5.0' }, signal: AbortSignal.timeout(5_000) });
        if (!check.ok) return;
        const publishedAt = verifiedXPost(await check.text(), item.originalUrl, item.title);
        if (publishedAt) { item.confidence = 'HIGH'; item.publishedAt = publishedAt; }
      } catch { /* Mirror alone does not verify the original post. */ }
    }));
    return mapped;
  }
};

const domesticDomains = new Set(['news.cn','xinhuanet.com','people.com.cn','thepaper.cn','cctv.com','yicai.com','caixin.com',
  'chinanews.com.cn','cna.com.tw','rthk.hk','bbc.com']);
const globalDomains = new Set(['reuters.com','apnews.com','bbc.com','bbc.co.uk','theguardian.com','cnn.com','dw.com']);
function mediaDomain(domain: string, domains: Set<string>): string | null {
  const matched = [...domains].find(base => officialDomain(domain, base));
  if (!matched) return null;
  return matched === 'bbc.co.uk' ? 'bbc.com' : matched;
}
function hotAdapter(id: 'cn' | 'global'): SourceAdapter {
  const china = id === 'cn';
  return {
    id: `${id}-gdelt`, accountId: id, homeUrl: 'https://www.gdeltproject.org/',
    async fetchItems() {
      const articles = await gdeltItems(china ? 'sourcelang:Chinese' : 'sourcelang:English');
      return articles.flatMap(item => {
        const domain = mediaDomain(item.domain, china ? domesticDomains : globalDomains);
        if (!domain) return [];
        return [{ externalId: item.url, title: item.title, body: item.title,
          originalUrl: item.url, publishedAt: item.publishedAt, domain,
          sourceName: domain, confidence: 'HIGH', topicId: china ? '国内' : '全球',
          importance: 55, breaking: false } as RawItem];
      });
    }
  };
}

type PublicFeed = { url: string; domain: string; name: string };
const chinaFeeds: PublicFeed[] = [
  { url: 'https://www.chinanews.com.cn/rss/finance.xml', domain: 'chinanews.com.cn', name: '中国新闻网' },
  { url: 'https://www.chinanews.com.cn/rss/china.xml', domain: 'chinanews.com.cn', name: '中国新闻网' },
  { url: 'https://www.chinanews.com.cn/rss/society.xml', domain: 'chinanews.com.cn', name: '中国新闻网' },
  { url: 'https://feeds.feedburner.com/rsscna/mainland', domain: 'cna.com.tw', name: '中央社' },
  { url: 'https://rthk.hk/rthk/news/rss/c_expressnews_greaterchina.xml', domain: 'rthk.hk', name: '香港电台' }
];
const worldFeeds: PublicFeed[] = [
  { url: 'https://www.france24.com/en/rss', domain: 'france24.com', name: 'France 24' },
  { url: 'https://www.aljazeera.com/xml/rss/all.xml', domain: 'aljazeera.com', name: '半岛电视台' },
  { url: 'https://news.un.org/feed/subscribe/en/news/all/rss.xml', domain: 'un.org', name: '联合国新闻' },
  { url: 'https://feeds.bbci.co.uk/news/world/rss.xml', domain: 'bbc.co.uk', name: 'BBC News' },
  { url: 'https://www.theguardian.com/world/rss', domain: 'theguardian.com', name: 'The Guardian' },
  { url: 'https://rss.dw.com/rdf/rss-en-world', domain: 'dw.com', name: 'Deutsche Welle' }
];
function hotRssAdapter(id: 'cn' | 'global'): SourceAdapter {
  const china = id === 'cn';
  return {
    id: `${id}-rss`, accountId: id, homeUrl: china ? 'https://www.chinanews.com.cn/rss/' : 'https://www.theguardian.com/help/feeds',
    async fetchItems() {
      const feeds = china ? chinaFeeds : worldFeeds;
      const fetched = await Promise.allSettled(feeds.map(async feed => ({ feed, items: rssItems(await getText(feed.url)) })));
      if (fetched.filter(result => result.status === 'fulfilled').length < 2) throw Error('Fewer than two public feeds available');
      const cutoff = Date.now() - 48 * 60 * 60_000;
      return fetched.flatMap(result => result.status === 'fulfilled' ? result.value.items.slice(0, 15).flatMap(item => {
        if (!officialDomain(new URL(item.link).hostname.toLowerCase(), result.value.feed.domain) || item.publishedAt < cutoff) return [];
        const detail = item.body && item.body !== item.title ? `\n\n${item.body.slice(0, 900)}` : '';
        return [{ externalId: item.link, title: item.title, body: `${item.title}${detail}`,
          originalUrl: item.link, publishedAt: item.publishedAt, domain: result.value.feed.domain,
          sourceName: result.value.feed.name, confidence: 'HIGH' as const,
          topicId: china ? '国内' as const : '全球' as const, importance: 60, breaking: false }];
      }) : []).sort((a, b) => b.publishedAt - a.publishedAt).slice(0, 40);
    }
  };
}
export function mediaRssAdapter(accountId: string, feed: PublicFeed, topicId: '国内' | '全球'): SourceAdapter {
  return { id: accountId + '-rss', accountId, homeUrl: 'https://' + feed.domain,
    async fetchItems() {
      return rssItems(await getText(feed.url)).filter(item => {
        try { return officialDomain(new URL(item.link).hostname, feed.domain) && item.publishedAt >= Date.now() - 48 * 60 * 60_000; }
        catch { return false; }
      }).slice(0, 5).map(item => ({ externalId: item.link, title: item.title,
        body: item.body && item.body !== item.title ? item.title + '\n\n' + item.body : item.title,
        originalUrl: item.link, publishedAt: item.publishedAt, domain: feed.domain, sourceName: feed.name,
        confidence: 'HIGH' as const, topicId, importance: 52, breaking: false }));
    }
  };
}
export const mediaAdapters: SourceAdapter[] = [
  mediaRssAdapter('media-chinanews', {url:'https://www.chinanews.com.cn/rss/china.xml',domain:'chinanews.com.cn',name:'中国新闻网'}, '国内'),
  mediaRssAdapter('media-france24', {url:'https://www.france24.com/en/rss',domain:'france24.com',name:'France 24'}, '全球'),
  mediaRssAdapter('media-aljazeera', {url:'https://www.aljazeera.com/xml/rss/all.xml',domain:'aljazeera.com',name:'半岛电视台'}, '全球'),
  mediaRssAdapter('media-un', {url:'https://news.un.org/feed/subscribe/en/news/all/rss.xml',domain:'un.org',name:'联合国新闻'}, '全球')
];
export const adapters: SourceAdapter[] = [officialF1, fia, openai, wuxing, jiangnan, radar,
  hotAdapter('cn'), hotAdapter('global'), hotRssAdapter('cn'), hotRssAdapter('global'), ...mediaAdapters];

import type { D1Database, RawItem, SourceAdapter } from './types.ts';

export function normalizedTitle(title: string): string {
  return title.toLowerCase().normalize('NFKC')
    .replace(/\s*[-|—]\s*(reuters|ap news|bbc|新华社|央视新闻|澎湃新闻).*$/i, '')
    .replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
}
function shingles(title: string): Set<string> {
  const value = normalizedTitle(title);
  if (/\p{Script=Han}/u.test(value)) {
    const compact = value.replace(/\s+/g, '');
    return new Set([...compact].slice(0, -1).map((char, index) => char + [...compact][index + 1]));
  }
  return new Set(value.split(/\s+/).filter(word => word.length > 2));
}
export function titleSimilarity(a: string, b: string): number {
  const x = shingles(a), y = shingles(b);
  if (!x.size || !y.size) return 0;
  let shared = 0;
  for (const part of x) if (y.has(part)) shared++;
  return shared / (x.size + y.size - shared);
}
export function sameEvent(a: string, b: string): boolean {
  const score = titleSimilarity(a, b);
  if (/\p{Script=Han}/u.test(a) || /\p{Script=Han}/u.test(b)) return score >= 0.35;
  const words = (title: string) => new Set((normalizedTitle(title).match(/[a-z]{4,}/g) ?? [])
    .filter(word => !['after','about','amid','from','into','more','than','that','their','there','this','with','world','news'].includes(word))
    .map(word => word.replace(/(?:ing|ed|ers|er|es|s)$/, '')));
  const left = words(a), right = words(b);
  const shared = [...left].filter(word => right.has(word)).length;
  return score >= 0.18 && shared >= 3;
}
async function hash(input: string): Promise<string> {
  const bytes = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return [...new Uint8Array(digest)].slice(0, 16).map(x => x.toString(16).padStart(2, '0')).join('');
}
export function canonicalUrl(input: string): string {
  if (!input.startsWith('https://')) return input;
  const url = new URL(input);
  url.hash = '';
  for (const key of [...url.searchParams.keys()]) {
    if (key.toLowerCase().startsWith('utm_') || ['fbclid', 'gclid', 'ref'].includes(key.toLowerCase())) url.searchParams.delete(key);
  }
  return url.toString();
}
async function source(db: D1Database, adapter: SourceAdapter, item: RawItem): Promise<string> {
  const id = `${adapter.accountId}:${item.domain}`;
  await db.prepare('INSERT OR IGNORE INTO sources(id,account_id,name,home_url,status) VALUES(?,?,?,?,?)')
    .bind(id, adapter.accountId, item.sourceName,
      adapter.accountId === 'cn' || adapter.accountId === 'global' ? `https://${item.domain}/` : adapter.homeUrl, 'OK').run();
  return id;
}
type Existing = { id: string; title: string; source_id: string; domain: string; original_url: string; published_at: number; post_id: string | null };
async function attach(db: D1Database, postId: string, sourceId: string, url: string): Promise<void> {
  await db.prepare('INSERT OR IGNORE INTO post_sources(post_id,source_id,original_url) VALUES(?,?,?)').bind(postId, sourceId, url).run();
}
async function addPost(db: D1Database, id: string, accountId: string, item: RawItem, updatedAt: number): Promise<void> {
  await db.prepare('INSERT OR IGNORE INTO posts(id,account_id,body,published_at,updated_at,importance,confidence,breaking,original_url) VALUES(?,?,?,?,?,?,?,?,?)')
    .bind(id, accountId, item.body, item.publishedAt, updatedAt,
      item.importance, item.confidence, item.breaking ? 1 : 0, item.originalUrl).run();
  await db.prepare('INSERT OR IGNORE INTO post_topics(post_id,topic_id) VALUES(?,?)').bind(id, item.topicId).run();
}
export async function ingestItem(db: D1Database, adapter: SourceAdapter, item: RawItem, now = Date.now()): Promise<{ added: boolean; breakingPostId?: string }> {
  if (!item.originalUrl.startsWith('https://') || !item.title || !Number.isFinite(item.publishedAt)) return { added: false };
  const rawId = await hash(`${adapter.id}:${canonicalUrl(item.externalId)}`);
  const existing = await db.prepare('SELECT id,title,post_id FROM raw_items WHERE id=?').bind(rawId).first<Existing>();
  if (existing) {
    if (existing.post_id && item.confidence === 'HIGH') {
      await db.prepare('UPDATE posts SET confidence=?,updated_at=MAX(updated_at+1,?) WHERE id=? AND confidence<>?')
        .bind('HIGH', now, existing.post_id, 'HIGH').run();
    }
    if (existing.post_id) {
      const current = await db.prepare('SELECT body,original_url FROM posts WHERE id=?').bind(existing.post_id).first<{body:string;original_url:string}>();
      const body = item.body;
      if (existing.title !== item.title) await db.prepare('UPDATE raw_items SET title=? WHERE id=?').bind(item.title, rawId).run();
      if (current && canonicalUrl(current.original_url) === canonicalUrl(item.originalUrl) && current.body !== body) {
        await db.prepare('UPDATE posts SET body=?,updated_at=MAX(updated_at+1,?) WHERE id=?')
          .bind(body, now, existing.post_id).run();
      }
    }
    return { added: false };
  }
  const sourceId = await source(db, adapter, item);

  const hot = adapter.accountId === 'cn' || adapter.accountId === 'global';
  if (hot) {
    const recent = (await db.prepare('SELECT id,title,source_id,domain,original_url,published_at,post_id FROM raw_items WHERE account_id=? AND published_at>? ORDER BY published_at DESC LIMIT 60')
      .bind(adapter.accountId, item.publishedAt - 24 * 60 * 60_000).all<Existing>()).results;
    const match = recent.filter(row => row.domain !== item.domain &&
      Math.abs(row.published_at - item.publishedAt) <= 24 * 60 * 60_000 && sameEvent(row.title, item.title))
      .sort((a, b) => Number(!!b.post_id) - Number(!!a.post_id))[0];
    if (!match) {
      await db.prepare('INSERT INTO raw_items(id,account_id,source_id,domain,title,original_url,published_at,post_id) VALUES(?,?,?,?,?,?,?,NULL)')
        .bind(rawId, adapter.accountId, sourceId, item.domain, item.title, item.originalUrl, item.publishedAt).run();
      return { added: false };
    }
    const postId = match.post_id ?? await hash(`${adapter.accountId}:${match.id}`);
    if (!match.post_id) {
      await addPost(db, postId, adapter.accountId, item, now);
      await attach(db, postId, match.source_id, match.original_url);
      await db.prepare('UPDATE raw_items SET post_id=? WHERE id=?').bind(postId, match.id).run();
    } else {
      await db.prepare('UPDATE posts SET updated_at=MAX(updated_at+1,?) WHERE id=?').bind(now, postId).run();
    }
    await db.prepare('INSERT INTO raw_items(id,account_id,source_id,domain,title,original_url,published_at,post_id) VALUES(?,?,?,?,?,?,?,?)')
      .bind(rawId, adapter.accountId, sourceId, item.domain, item.title, item.originalUrl, item.publishedAt, postId).run();
    await attach(db, postId, sourceId, item.originalUrl);
    return { added: !match.post_id };
  }

  const postId = await hash(`${adapter.accountId}:${canonicalUrl(item.originalUrl)}`);
  await addPost(db, postId, adapter.accountId, item, now);
  await db.prepare('INSERT INTO raw_items(id,account_id,source_id,domain,title,original_url,published_at,post_id) VALUES(?,?,?,?,?,?,?,?)')
    .bind(rawId, adapter.accountId, sourceId, item.domain, item.title, item.originalUrl, item.publishedAt, postId).run();
  await attach(db, postId, sourceId, item.originalUrl);
  const freshBreaking = item.breaking && item.confidence === 'OFFICIAL'
    && item.publishedAt >= now - 24 * 60 * 60_000 && item.publishedAt <= now + 5 * 60_000;
  return { added: true, breakingPostId: freshBreaking ? postId : undefined };
}

export async function syncPage(db: D1Database, cursor: string | null): Promise<unknown> {
  let updatedAt = 0, id = '';
  if (cursor) {
    try {
      const decoded = JSON.parse(atob(cursor)) as [number, string];
      if (!Number.isFinite(decoded[0]) || typeof decoded[1] !== 'string') throw Error('invalid');
      [updatedAt, id] = decoded;
    } catch { throw Error('Invalid cursor'); }
  }
  type Row = { id: string; account_id: string; body: string; published_at: number; updated_at: number; importance: number; confidence: string; breaking: number; original_url: string };
  const posts = (await db.prepare('SELECT * FROM posts WHERE updated_at>? OR (updated_at=? AND id>?) ORDER BY updated_at,id LIMIT 50')
    .bind(updatedAt, updatedAt, id).all<Row>()).results;
  const ids = posts.map(post => post.id);
  const placeholders = ids.map(() => '?').join(',');
  const topics = ids.length ? (await db.prepare(`SELECT post_id,topic_id FROM post_topics WHERE post_id IN (${placeholders})`)
    .bind(...ids).all<{post_id:string;topic_id:string}>()).results : [];
  const links = ids.length ? (await db.prepare(`SELECT post_id,source_id,original_url FROM post_sources WHERE post_id IN (${placeholders})`)
    .bind(...ids).all<{post_id:string;source_id:string;original_url:string}>()).results : [];
  const output = [];
  for (const post of posts) {
    output.push({ id: post.id, accountId: post.account_id, body: post.body, publishedAt: post.published_at,
      updatedAt: post.updated_at, importance: post.importance, confidence: post.confidence,
      breaking: !!post.breaking, originalUrl: post.original_url,
      topicIds: topics.filter(row => row.post_id === post.id).map(row => row.topic_id),
      sources: links.filter(row => row.post_id === post.id).map(row => ({ sourceId: row.source_id, originalUrl: row.original_url })) });
  }
  const accounts = (await db.prepare('SELECT id,name,handle FROM accounts').all<{id:string;name:string;handle:string}>()).results;
  const allSources = (await db.prepare('SELECT id,account_id,name,home_url,status FROM sources').all<{id:string;account_id:string;name:string;home_url:string;status:string}>()).results;
  const sources = allSources.map(row => ({ id: row.id, accountId: row.account_id, name: row.name, homeUrl: row.home_url, status: row.status }));
  const last = posts.at(-1);
  return { posts: output, accounts, sources, nextCursor: last ? btoa(JSON.stringify([last.updated_at, last.id])) : cursor ?? '' };
}

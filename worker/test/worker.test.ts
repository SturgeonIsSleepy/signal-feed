import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { adapters, mediaRssAdapter, f1ArticleBody, f1ArticleCards, fiaDocuments, isJiangnanK80Ultra, isOpenAiProductUpdate, isWuxingF1, isWuxingPost, pageDescription, rssItems, verifiedXPost } from '../src/adapters.ts';
import { canonicalUrl, ingestItem, sameEvent, syncPage, titleSimilarity } from '../src/storage.ts';
import { refreshF1, refreshAi, driverHistory } from '../src/snapshots.ts';
import { translatePost, translationChunks } from '../src/translation.ts';
import { runAdapter } from '../src/index.ts';
import type { D1Database, D1Statement, RawItem, SourceAdapter } from '../src/types.ts';

function database(): D1Database {
  const sqlite = new DatabaseSync(':memory:');
  sqlite.exec(fs.readFileSync(new URL('../migrations/0001_init.sql', import.meta.url), 'utf8'));
  sqlite.exec(fs.readFileSync(new URL('../migrations/0002_translations.sql', import.meta.url), 'utf8'));
  return { prepare(sql: string): D1Statement {
    let args: unknown[] = [];
    return {
      bind(...values: unknown[]) { args = values; return this; },
      async first<T>() { return sqlite.prepare(sql).get(...args) as T | null ?? null; },
      async all<T>() { return { results: sqlite.prepare(sql).all(...args) as T[] }; },
      async run() { return sqlite.prepare(sql).run(...args); }
    };
  } };
}
const adapter = (id: string): SourceAdapter => ({ id, accountId: id, homeUrl: 'https://example.org', fetchItems: async () => [] });
test('media feeds accept fresh publisher links, preserve body and bound each batch', async () => {
  const originalFetch = globalThis.fetch;
  const entry = (url: string, date: number) => `<item><title>Report</title><link>${url}</link><pubDate>${new Date(date).toUTCString()}</pubDate><description>${'Full paragraph '.repeat(500)}</description></item>`;
  globalThis.fetch = async () => new Response('<rss>' + entry('https://example.org/old', 0) + entry('https://example.org.evil.test/fake', Date.now()) + Array.from({length:8}, (_, i) => entry('https://example.org/' + i, Date.now())).join('') + '</rss>');
  try {
    const media = mediaRssAdapter('media-test', {url:'https://example.org/rss', domain:'example.org', name:'Publisher'}, '全球');
    const items = await media.fetchItems(null);
    assert.equal(items.length, 5);
    assert.equal(items[0].originalUrl, 'https://example.org/0');
    assert.ok(items[0].body.length > 6000);
    assert.equal(items[0].breaking, false);
  } finally { globalThis.fetch = originalFetch; }
});
function item(url: string, title: string, domain: string, topicId: RawItem['topicId'] = '国内'): RawItem {
  return { externalId: url, title, body: title, originalUrl: url, domain, sourceName: domain,
    publishedAt: Date.parse('2026-09-25T12:00:00Z'), confidence: 'HIGH', topicId,
    importance: 55, breaking: false };
}

test('strict Redmi K80 Ultra author and F1 filters', () => {
  assert.equal(isJiangnanK80Ultra('K80 至尊版 官改发布', '<h1>江南烟雨断桥殇</h1>'), true);
  assert.equal(isJiangnanK80Ultra('K80 Pro 官改发布', '江南烟雨断桥殇 K80 至尊版'), false);
  assert.equal(isJiangnanK80Ultra('K80 至尊版 官改发布', '其他作者'), false);
  assert.equal(isWuxingF1('F1 阿塞拜疆大奖赛排位赛'), true);
  assert.equal(isWuxingF1('中超联赛今晚开球'), false);
  assert.equal(isWuxingF1('世界赛艇联合会上海冲刺赛正在直播'), false);
  assert.equal(isWuxingF1('F1摩托艇大奖赛'), false);
  assert.equal(isWuxingPost('五星体育 2026-09-21 F1转播计划', 'https://www.sina.cn/news/detail/5345575903955383.html'), true);
  assert.equal(isWuxingPost('其他媒体 F1转播计划', 'https://www.sina.cn/news/detail/5345575903955383.html'), false);
  assert.equal(isOpenAiProductUpdate('ChatGPT usage limits updated'), true);
  assert.equal(isOpenAiProductUpdate('How a researcher uses Codex and ChatGPT to search for molecules'), false);
  assert.equal(isOpenAiProductUpdate('Helping older adults use AI in everyday life'), false);
});

test('RSS parsing retains original link and publish time', () => {
  const results = rssItems('<rss><item><title>Codex &amp; ChatGPT</title><link>https://openai.com/news/test</link><pubDate>Fri, 25 Sep 2026 12:00:00 GMT</pubDate><description>Update</description></item></rss>');
  assert.equal(results[0].title, 'Codex & ChatGPT');
  assert.equal(results[0].link, 'https://openai.com/news/test');
  assert.equal(results[0].publishedAt, Date.parse('2026-09-25T12:00:00Z'));
  const rdf = rssItems('<item rdf:about="x"><title>World news</title><link>https://dw.com/a</link><dc:date>2026-09-25T12:00:00Z</dc:date></item>');
  assert.equal(rdf[0].publishedAt, results[0].publishedAt);
  assert.equal(pageDescription('<meta name="description" content="Driver&#x27;s race update">'), "Driver's race update");
});
test('official F1 and FIA pages yield original links and document dates', () => {
  const f1 = f1ArticleCards('<a href="/en/latest/article/race-report.abc">Race report</a><a href="/en/latest/article/f2-race.xyz">F2 race</a>');
  assert.deepEqual(f1, [{ title: 'Race report', url: 'https://www.formula1.com/en/latest/article/race-report.abc' }]);
  const fia = fiaDocuments('<li class="document-row"><a href="/system/files/decision-document/ruling.pdf"><div class="title">Doc 4 - Decision - Car 1</div><div class="published">Published on <span class="date-display-single">25.09.26 12:35</span> CET</div></a></li>');
  assert.equal(fia.length, 1);
  assert.equal(fia[0].url, 'https://www.fia.com/system/files/decision-document/ruling.pdf');
  assert.equal(fia[0].publishedAt, Date.parse('2026-09-25T12:35:00+02:00'));
});

test('F1 detail keeps all publicly available article paragraphs', () => {
  const article = { isProtected: false, fullContent: [
    { text: 'First [driver](https://www.formula1.com/driver) paragraph.' },
    { text: 'Second paragraph.\n\nFinal paragraph.' }
  ] };
  const page = `<meta name="description" content="Short excerpt"><script>self.__next_f.push(${JSON.stringify([1, `6:${JSON.stringify(article)}`])})</script>`;
  assert.equal(f1ArticleBody(page, 'Race report'), 'Race report\n\nFirst driver paragraph.\n\nSecond paragraph.\n\nFinal paragraph.');
  const protectedPage = `<meta name="description" content="Short excerpt"><script>self.__next_f.push(${JSON.stringify([1, `6:${JSON.stringify({ ...article, isProtected: true })}`])})</script>`;
  assert.equal(f1ArticleBody(protectedPage, 'Race report'), 'Race report\n\nShort excerpt');
  const text = '最后一段：完整内容。';
  const reference = `38:T${new TextEncoder().encode(text).length.toString(16)},${text}`;
  const referenced = `<script>self.__next_f.push(${JSON.stringify([1, reference])})</script>` +
    `<script>self.__next_f.push(${JSON.stringify([1, `6:${JSON.stringify({ isProtected: false, fullContent: [{ text: '$38' }] })}`])})</script>`;
  assert.equal(f1ArticleBody(referenced, 'Title'), `Title\n\n${text}`);
});
test('radar confidence requires matching original X metadata', () => {
  const url = 'https://x.com/thsottiaux/status/123';
  const html = '<meta property="og:url" content="https://x.com/thsottiaux/status/123"/><meta property="og:description" content="ChatGPT feature is live"/><meta property="article:published_time" content="2026-09-25T12:00:00Z"/>';
  assert.equal(verifiedXPost(html, url, 'ChatGPT feature is live'), Date.parse('2026-09-25T12:00:00Z'));
  assert.equal(verifiedXPost(html, url, 'Different text'), null);
  assert.equal(verifiedXPost(html, 'https://x.com/other/status/123', 'ChatGPT feature is live'), null);
});
test('tracking parameters do not create a second identity', () => {
  assert.equal(canonicalUrl('https://example.org/a?utm_source=x&n=1#section'), 'https://example.org/a?n=1');
});
test('cross-media headlines about the same event match without joining unrelated news', () => {
  assert.equal(sameEvent('Rebel offensive against Ethiopian army stokes fears of return to civil war',
    "New fighting in Ethiopia's Tigray region raises fears of a return to the country's civil war"), true);
  assert.equal(sameEvent('Italy to ban burqa in schools, limit foreign pupils',
    'Italy ministers agree to ban burqa and niqab in school and cap foreigners in class'), true);
  assert.equal(sameEvent('Italy to ban burqa in schools, limit foreign pupils',
    'Oil prices rise after meeting of world leaders'), false);
});

test('public RSS adapter keeps current original media links from different outlets', async () => {
  const original = globalThis.fetch;
  const date = new Date().toUTCString();
  globalThis.fetch = async input => {
    const host = new URL(String(input)).hostname;
    const link = host.includes('bbc') ? 'https://www.bbc.co.uk/news/articles/story-1'
      : host.includes('guardian') ? 'https://www.theguardian.com/world/story-2'
      : 'https://www.dw.com/en/story/a-123';
    return new Response(`<rss><item><title>World leaders meet</title><link>${link}</link><pubDate>${date}</pubDate><description>Public summary</description></item></rss>`);
  };
  try {
    const items = await adapters.find(item => item.id === 'global-rss')!.fetchItems();
    assert.deepEqual(new Set(items.map(item => item.domain)), new Set(['bbc.co.uk', 'theguardian.com', 'dw.com']));
    assert.ok(items.every(item => item.originalUrl.startsWith('https://')));
    assert.ok(items.every(item => item.body.includes('Public summary')));
  } finally { globalThis.fetch = original; }
});

test('sync exposes accounts before the first post so they can be followed', async () => {
  const page = await syncPage(database(), null) as any;
  assert.equal(page.posts.length, 0);
  assert.equal(page.accounts.length, 8);
});

test('single hot report stays hidden; second distinct source creates one post; repeats are idempotent', async () => {
  const db = database(), source = adapter('cn');
  const first = item('https://news.cn/a', '某地发布新能源政策', 'news.cn');
  const second = item('https://thepaper.cn/b', '某地发布新能源政策', 'thepaper.cn');
  assert.ok(titleSimilarity(first.title, second.title) > 0.52);
  assert.equal((await ingestItem(db, source, first)).added, false);
  assert.equal((await syncPage(db, null) as any).posts.length, 0);
  assert.equal((await ingestItem(db, source, second)).added, true);
  assert.equal((await ingestItem(db, source, second)).added, false);
  const page = await syncPage(db, null) as any;
  assert.equal(page.posts.length, 1);
  assert.equal(page.posts[0].sources.length, 2);
  assert.equal(page.sources.length, 2);
  assert.equal((await syncPage(db, page.nextCursor) as any).posts.length, 0);
});

test('official post is synced once and a changed title is synced again', async () => {
  const db = database(), source = adapter('f1');
  const first = { ...item('https://formula1.com/news/a', 'Breaking: decision', 'formula1.com', 'F1'),
    confidence: 'OFFICIAL' as const, breaking: true };
  const added = await ingestItem(db, source, first, first.publishedAt);
  assert.equal(added.added, true);
  assert.ok(added.breakingPostId);
  const page = await syncPage(db, null) as any;
  assert.equal(page.posts.length, 1);
  assert.equal((await ingestItem(db, source, first, first.publishedAt + 1)).added, false);
  await ingestItem(db, source, { ...first, title: 'Breaking: updated decision', body: 'Breaking: updated decision' }, first.publishedAt + 2);
  const updates = await syncPage(db, page.nextCursor) as any;
  assert.equal(updates.posts.length, 1);
  assert.equal(updates.posts[0].body, 'Breaking: updated decision');
});
test('a richer source description updates the existing post without duplication', async () => {
  const db = database(), source = adapter('f1');
  const first = item('https://formula1.com/news/b', 'Race report', 'formula1.com', 'F1');
  await ingestItem(db, source, first, 100);
  const page = await syncPage(db, null) as any;
  await ingestItem(db, source, { ...first, body: 'Race report\n\nFull public summary.' }, 101);
  const changes = await syncPage(db, page.nextCursor) as any;
  assert.equal(changes.posts.length, 1);
  assert.equal(changes.posts[0].body, 'Race report\n\nFull public summary.');
  assert.equal((await syncPage(db, null) as any).posts.length, 1);
  const fullBody = '完整正文。'.repeat(1500);
  await ingestItem(db, source, { ...first, body: fullBody }, 102);
  assert.equal((await syncPage(db, changes.nextCursor) as any).posts[0].body, fullBody);
});

test('a full sync page batches queries and keeps each post source attached correctly', async () => {
  const db = database(), source = adapter('f1');
  for (let index = 0; index < 50; index++) {
    await ingestItem(db, source, item(`https://formula1.com/news/${index}`, `Report ${index}`, 'formula1.com', 'F1'), 100 + index);
  }
  let queries = 0;
  const prepare = db.prepare;
  db.prepare = sql => { queries++; return prepare(sql); };
  const page = await syncPage(db, null) as any;
  assert.equal(page.posts.length, 50);
  assert.equal(queries, 5);
  for (const post of page.posts) {
    assert.deepEqual(post.topicIds, ['F1']);
    assert.equal(post.sources.length, 1);
    assert.equal(post.sources[0].originalUrl, post.originalUrl);
  }
});
test('old official decisions are stored without a stale Breaking push', async () => {
  const db = database(), source = adapter('fia');
  const old = { ...item('https://fia.com/decision.pdf', 'Decision', 'fia.com', 'F1'),
    publishedAt: Date.now() - 2 * 24 * 60 * 60_000, confidence: 'OFFICIAL' as const, breaking: true };
  const result = await ingestItem(db, source, old);
  assert.equal(result.added, true);
  assert.equal(result.breakingPostId, undefined);
});
test('verified radar post upgrades confidence in incremental sync', async () => {
  const db = database(), source = adapter('radar');
  const first = { ...item('https://x.com/thsottiaux/status/123', 'ChatGPT update', 'x.com', 'AI'),
    confidence: 'UNCONFIRMED' as const };
  await ingestItem(db, source, first, 100);
  const page = await syncPage(db, null) as any;
  await ingestItem(db, source, { ...first, confidence: 'HIGH' }, 101);
  const update = await syncPage(db, page.nextCursor) as any;
  assert.equal(update.posts[0].confidence, 'HIGH');
});

test('adapter failure is recorded without deleting cached source', async () => {
  const db = database();
  await db.prepare('INSERT INTO sources VALUES(?,?,?,?,?)').bind('f1:formula1.com','f1','Formula 1','https://formula1.com','OK').run();
  const broken: SourceAdapter = { id: 'broken-f1', accountId: 'f1', homeUrl: 'https://formula1.com',
    fetchItems: async () => { throw Error('upstream unavailable'); } };
  assert.equal(await runAdapter({ DB: db }, broken), 0);
  const status = await db.prepare('SELECT status FROM sources WHERE id=?').bind('f1:formula1.com').first<{status:string}>();
  const state = await db.prepare('SELECT last_error FROM adapter_state WHERE id=?').bind('broken-f1').first<{last_error:string}>();
  assert.equal(status?.status, 'ERROR');
  assert.match(state?.last_error ?? '', /upstream unavailable/);
});
test('rate-limited GDELT adapter waits before retrying', async () => {
  const db = database();
  await db.prepare('INSERT INTO adapter_state(id,last_error,checked_at) VALUES(?,?,?)')
    .bind('cn-gdelt', 'Error: 429 api.gdeltproject.org', Date.now()).run();
  const source: SourceAdapter = { id: 'cn-gdelt', accountId: 'cn', homeUrl: 'https://www.gdeltproject.org/',
    fetchItems: async () => { throw Error('Fetched too early'); } };
  assert.equal(await runAdapter({ DB: db }, source), 0);
  const state = await db.prepare('SELECT last_error FROM adapter_state WHERE id=?').bind('cn-gdelt').first<{last_error:string}>();
  assert.equal(state?.last_error, 'Error: 429 api.gdeltproject.org');
});

test('F1 and AI snapshots map API data and retain ranking movement', async () => {
  const db = database();
  const original = globalThis.fetch;
  let reversed = false;
  globalThis.fetch = async (input) => {
    const url = String(input);
    let payload: unknown;
    if (url.includes('driverstandings')) payload = { MRData: { StandingsTable: { StandingsLists: [{ DriverStandings: [
      { position: '1', Driver: { givenName: 'A', familyName: 'Driver' }, Constructors: [{ name: 'Team' }], points: '25' }
    ] }] } } };
    else if (url.includes('constructorstandings')) payload = { MRData: { StandingsTable: { StandingsLists: [{ ConstructorStandings: [
      { position: '1', Constructor: { name: 'Team' }, points: '25' }
    ] }] } } };
    else if (url.includes('/last/results')) payload = { MRData: { RaceTable: { Races: [] } } };
    else if (url.includes('jolpi.ca')) payload = { MRData: { RaceTable: { Races: [
      { round: '1', raceName: 'Future Grand Prix', date: '2099-09-25', time: '12:00:00Z', Circuit: { circuitName: 'Circuit', Location: { locality: 'City' } } }
    ] } } };
    else payload = { intelligence_index_version: 4.1, pagination: { has_more: false }, data: [
      { id: 'a', name: 'A', model_creator: { name: 'Maker' }, evaluations: { artificial_analysis_intelligence_index: reversed ? 80 : 90, artificial_analysis_coding_index: 75, artificial_analysis_agentic_index: null }, pricing: { price_1m_input_tokens: 1, price_1m_output_tokens: 2 }, performance: { median_output_tokens_per_second: 100, median_time_to_first_token_seconds: 0.6 } },
      { id: 'b', name: 'B', model_creator: { name: 'Maker' }, evaluations: { artificial_analysis_intelligence_index: reversed ? 90 : 80 }, pricing: { price_1m_input_tokens: 3, price_1m_output_tokens: 4 }, performance: { median_output_tokens_per_second: 50 } }
    ] };
    return new Response(JSON.stringify(payload), { status: 200 });
  };
  try {
    await refreshF1({ DB: db });
    const f1 = JSON.parse((await db.prepare('SELECT payload FROM snapshots WHERE id=?').bind('f1').first<{payload:string}>())!.payload);
    assert.equal(f1.next.name, 'Future Grand Prix');
    assert.equal(f1.drivers[0].points, 25);
    await refreshAi({ DB: db, AA_API_KEY: 'test' });
    reversed = true;
    await refreshAi({ DB: db, AA_API_KEY: 'test' });
    const ai = JSON.parse((await db.prepare('SELECT payload FROM snapshots WHERE id=?').bind('ai').first<{payload:string}>())!.payload);
    assert.equal(ai.models[0].id, 'b');
    assert.equal(ai.models[0].change, 1);
    assert.equal(ai.models[1].evaluations.artificial_analysis_coding_index, 75);
    assert.equal(ai.models[1].latency, 0.6);
    assert.equal('artificial_analysis_agentic_index' in ai.models[1].evaluations, false);
  } finally { globalThis.fetch = original; }
});

test('history caches rounds, refreshes corrections, preserves failures and maps teams', async () => {
 const original=globalThis.fetch; const calls:string[]=[];
 globalThis.fetch=async input=>{
  const url=String(input); calls.push(url); const round=Number(url.match(/2026\/(\d+)/)?.[1]);
  if(round===2) throw Error('offline');
  const team=url.includes('constructorstandings');
  return new Response(JSON.stringify({MRData:{StandingsTable:{StandingsLists:[{round:String(round),
   [team?'ConstructorStandings':'DriverStandings']:[{points:'50',Driver:{driverId:'a',givenName:'A',familyName:'B'},Constructor:{constructorId:'t',name:'Team'}}]
  }]}}}));
 };
 try {
  const old=[{round:1,name:'One',drivers:[{id:'a',name:'A B',points:25}]},{round:2,name:'Two',drivers:[{id:'a',name:'A B',points:40}]}];
  const races=[{round:1,name:'One'},{round:2,name:'Two'},{round:3,name:'Three'}];
  const rows=await driverHistory(2026,races,3,old);
  assert.equal(calls.length,1);assert.equal(rows[2].drivers[0].points,50);
  const cached=await driverHistory(2026,races,2,old);
  assert.equal(cached[1].drivers[0].points,40);
  const teams=await driverHistory(2026,races,3,[],true);
  assert.deepEqual(teams.map(x=>x.round),[1,3]);assert.equal(teams[0].drivers[0].id,'t');
 } finally {globalThis.fetch=original;}
});

test('cloud translation validates original hash, caches results and enforces daily budget', async () => {
 const db=database(); await ingestItem(db,adapter('f1'),item('https://formula1.com/a','Qualifying on pole','formula1.com','F1'));
 const post=(await syncPage(db,null) as any).posts[0];
 const hash=Buffer.from(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(post.body))).toString('hex');
 let calls=0;const env={DB:db,AI:{run:async()=>{calls++;return {choices:[{finish_reason:'stop',message:{content:'排位赛获得杆位'}}]};}}};
 const request=(h:string)=>new Request('https://test/v1/translate?postId='+post.id+'&sourceHash='+h,{method:'POST'});
 assert.equal((await translatePost(request('wrong'),env)).status,409);
 assert.equal((await translatePost(request(hash),env)).status,200);
 assert.equal((await translatePost(request(hash),env)).status,200);assert.equal(calls,1);
 await db.prepare('DELETE FROM translations').run();
 await db.prepare('UPDATE translation_budget SET chunks=30').run();
 assert.equal((await translatePost(request(hash),env)).status,429);assert.equal(calls,1);
 const body=('Long paragraph. '.repeat(230)+'\n\n').repeat(4);
 const chunks=translationChunks(body);assert.equal(chunks.join(''),body);assert.ok(chunks.every(x=>x.length<=3000));
});

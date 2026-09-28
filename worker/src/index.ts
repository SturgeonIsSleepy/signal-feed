import { translatePost } from './translation.ts';
import { adapters } from './adapters.ts';
import { ingestItem, syncPage } from './storage.ts';
import { refreshF1, refreshAi } from './snapshots.ts';
import { pushBreaking } from './push.ts';
import type { Env, SourceAdapter } from './types.ts';

function response(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' } });
}
async function snapshot(env: Env, id: string): Promise<Response> {
  let row = await env.DB.prepare('SELECT payload,updated_at FROM snapshots WHERE id=?').bind(id)
    .first<{payload:string;updated_at:number}>();
  if (!row || (id === 'ai' && !JSON.parse(row.payload).models?.[0]?.evaluations)) {
    try {
      if (id === 'f1') await refreshF1(env);
      if (id === 'ai' && env.AA_API_KEY) await refreshAi(env);
      row = await env.DB.prepare('SELECT payload,updated_at FROM snapshots WHERE id=?').bind(id)
        .first<{payload:string;updated_at:number}>();
    } catch (error) { console.error(`${id} initial snapshot`, error); }
  }
  return row ? response({ ...JSON.parse(row.payload), updatedAt: row.updated_at }) : response({ error: 'Data not available yet' }, 503);
}
export async function runAdapter(env: Env, adapter: SourceAdapter): Promise<number> {
  const now = Date.now();
  if (adapter.id.endsWith('-gdelt')) {
    const state = await env.DB.prepare('SELECT last_error,checked_at FROM adapter_state WHERE id=?').bind(adapter.id)
      .first<{last_error:string|null;checked_at:number}>();
    if (state?.last_error?.includes('429') && now - state.checked_at < 6 * 60 * 60_000) return 0;
  }
  try {
    const items = await adapter.fetchItems();
    let added = 0;
    for (const item of items.slice(0, 40)) {
      const result = await ingestItem(env.DB, adapter, item, now);
      if (result.added) added++;
      if (result.breakingPostId) {
        try { await pushBreaking(env, result.breakingPostId, adapter.accountId, item.body); }
        catch (error) { console.error('Breaking push', error); }
      }
    }
    await env.DB.prepare('UPDATE sources SET status=? WHERE account_id=?').bind('OK', adapter.accountId).run();
    if (env.FCM_PROJECT_ID) {
      const pending = (await env.DB.prepare('SELECT p.id,p.account_id,p.body FROM posts p LEFT JOIN notified_posts n ON n.post_id=p.id WHERE p.breaking=1 AND p.confidence=? AND n.post_id IS NULL AND p.published_at>? ORDER BY p.published_at DESC LIMIT 3')
        .bind('OFFICIAL', now - 24 * 60 * 60_000).all<{id:string;account_id:string;body:string}>()).results;
      for (const post of pending) {
        try { await pushBreaking(env, post.id, post.account_id, post.body); }
        catch (error) { console.error('Pending push', error); }
      }
    }
    await env.DB.prepare('INSERT INTO adapter_state(id,last_seen_at,last_error,checked_at) VALUES(?,?,NULL,?) ON CONFLICT(id) DO UPDATE SET last_seen_at=excluded.last_seen_at,last_error=NULL,checked_at=excluded.checked_at')
      .bind(adapter.id, items[0]?.publishedAt ?? 0, now).run();
    return added;
  } catch (error) {
    await env.DB.prepare('UPDATE sources SET status=? WHERE account_id=?').bind('ERROR', adapter.accountId).run();
    await env.DB.prepare('INSERT INTO adapter_state(id,last_error,checked_at) VALUES(?,?,?) ON CONFLICT(id) DO UPDATE SET last_error=excluded.last_error,checked_at=excluded.checked_at')
      .bind(adapter.id, String(error).slice(0, 180), now).run();
    return 0;
  }
}
function scheduledAdapter(date: Date): SourceAdapter {
  const slot = Math.floor(date.getUTCMinutes() / 3);
  const phase = slot % 5;
  const urgent = adapters.filter(item => ['f1', 'fia', 'openai'].includes(item.accountId));
  const normal = adapters.filter(item => !['f1', 'fia', 'openai'].includes(item.accountId));
  const absoluteSlot = Math.floor(date.getTime() / 180_000);
  return phase < 3 ? urgent[phase % urgent.length] : normal[(Math.floor(absoluteSlot / 5) * 2 + phase - 3) % normal.length];
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === '/v1/translate' && request.method === 'POST') return translatePost(request, env);
    if (request.method !== 'GET') return response({ error: 'Method not allowed' }, 405);
    if (url.pathname === '/health') return response({ ok: true });
    if (url.pathname === '/v1/sync') {
      try { return response(await syncPage(env.DB, url.searchParams.get('cursor'))); }
      catch (error) {
        return String(error).includes('Invalid cursor') ? response({ error: 'Invalid cursor' }, 400)
          : response({ error: 'Sync unavailable' }, 503);
      }
    }
    if (url.pathname === '/v1/f1') return snapshot(env, 'f1');
    if (url.pathname === '/v1/ai') return snapshot(env, 'ai');
    if (url.pathname === '/v1/sources') {
      const rows = (await env.DB.prepare('SELECT * FROM adapter_state ORDER BY id').all()).results;
      return response({ adapters: rows });
    }
    return response({ error: 'Not found' }, 404);
  },
  async scheduled(controller: { scheduledTime: number; cron: string }, env: Env): Promise<void> {
    if (controller.cron === '0 0 * * *') { try { await refreshAi(env); } catch (error) { console.error('AI snapshot', error); } return; }
    if (controller.cron === '0 * * * *') { try { await refreshF1(env); } catch (error) { console.error('F1 snapshot', error); } return; }
    await runAdapter(env, scheduledAdapter(new Date(controller.scheduledTime)));
  }
};

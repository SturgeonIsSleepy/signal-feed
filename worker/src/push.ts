import type { Env } from './types.ts';

function base64url(bytes: Uint8Array): string {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}
async function accessToken(env: Env): Promise<string> {
  const pem = env.FCM_PRIVATE_KEY!.replace(/\\n/g, '\n').replace(/-----[^-]+-----/g, '').replace(/\s/g, '');
  const der = Uint8Array.from(atob(pem), char => char.charCodeAt(0));
  const key = await crypto.subtle.importKey('pkcs8', der, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
  const now = Math.floor(Date.now() / 1000);
  const header = base64url(new TextEncoder().encode(JSON.stringify({ alg: 'RS256', typ: 'JWT' })));
  const claim = base64url(new TextEncoder().encode(JSON.stringify({ iss: env.FCM_CLIENT_EMAIL,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token', iat: now, exp: now + 3600 })));
  const unsigned = `${header}.${claim}`;
  const signature = base64url(new Uint8Array(await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(unsigned))));
  const response = await fetch('https://oauth2.googleapis.com/token', { method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: `${unsigned}.${signature}` }) });
  if (!response.ok) throw Error(`FCM auth ${response.status}`);
  return (await response.json() as { access_token: string }).access_token;
}
export async function pushBreaking(env: Env, postId: string, accountId: string, body: string): Promise<void> {
  if (!env.FCM_PROJECT_ID || !env.FCM_CLIENT_EMAIL || !env.FCM_PRIVATE_KEY) return;
  const sent = await env.DB.prepare('SELECT post_id FROM notified_posts WHERE post_id=?').bind(postId).first();
  if (sent) return;
  const token = await accessToken(env);
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${env.FCM_PROJECT_ID}/messages:send`, {
    method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ message: { topic: 'breaking', data: { postId, accountId, body: body.slice(0, 180) },
      android: { priority: 'HIGH' } } })
  });
  if (!response.ok) throw Error(`FCM send ${response.status}`);
  await env.DB.prepare('INSERT OR IGNORE INTO notified_posts(post_id,notified_at) VALUES(?,?)').bind(postId, Date.now()).run();
}

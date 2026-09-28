export interface D1Statement {
  bind(...values: unknown[]): D1Statement;
  first<T = Record<string, unknown>>(): Promise<T | null>;
  all<T = Record<string, unknown>>(): Promise<{ results: T[] }>;
  run(): Promise<unknown>;
}
export interface D1Database { prepare(sql: string): D1Statement; }
export interface Env {
  DB: D1Database;
  AI?: { run(model: string, input: unknown): Promise<any> };
  AA_API_KEY?: string;
  FCM_PROJECT_ID?: string;
  FCM_CLIENT_EMAIL?: string;
  FCM_PRIVATE_KEY?: string;
}
export type Confidence = 'OFFICIAL' | 'HIGH' | 'UNCONFIRMED';
export interface RawItem {
  externalId: string;
  title: string;
  body: string;
  originalUrl: string;
  publishedAt: number;
  domain: string;
  sourceName: string;
  confidence: Confidence;
  topicId: 'F1' | 'AI' | '玩机' | '国内' | '全球';
  importance: number;
  breaking: boolean;
}
export interface SourceAdapter {
  id: string;
  accountId: string;
  homeUrl: string;
  fetchItems(): Promise<RawItem[]>;
}

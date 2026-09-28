CREATE TABLE IF NOT EXISTS accounts (
  id TEXT PRIMARY KEY, name TEXT NOT NULL, handle TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS sources (
  id TEXT PRIMARY KEY, account_id TEXT NOT NULL, name TEXT NOT NULL,
  home_url TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'OK'
);
CREATE TABLE IF NOT EXISTS topics (id TEXT PRIMARY KEY, name TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS posts (
  id TEXT PRIMARY KEY, account_id TEXT NOT NULL, body TEXT NOT NULL,
  published_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
  importance INTEGER NOT NULL, confidence TEXT NOT NULL,
  breaking INTEGER NOT NULL, original_url TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS posts_sync ON posts(updated_at, id);
CREATE INDEX IF NOT EXISTS posts_time ON posts(published_at DESC);
CREATE TABLE IF NOT EXISTS post_topics (
  post_id TEXT NOT NULL, topic_id TEXT NOT NULL,
  PRIMARY KEY(post_id, topic_id)
);
CREATE TABLE IF NOT EXISTS post_sources (
  post_id TEXT NOT NULL, source_id TEXT NOT NULL, original_url TEXT NOT NULL,
  PRIMARY KEY(post_id, source_id, original_url)
);
CREATE TABLE IF NOT EXISTS raw_items (
  id TEXT PRIMARY KEY, account_id TEXT NOT NULL, source_id TEXT NOT NULL,
  domain TEXT NOT NULL, title TEXT NOT NULL, original_url TEXT NOT NULL,
  published_at INTEGER NOT NULL, post_id TEXT
);
CREATE INDEX IF NOT EXISTS raw_items_recent ON raw_items(account_id, published_at DESC);
CREATE TABLE IF NOT EXISTS adapter_state (
  id TEXT PRIMARY KEY, last_seen_at INTEGER NOT NULL DEFAULT 0,
  last_error TEXT, checked_at INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS snapshots (
  id TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS ai_ranks (
  day TEXT NOT NULL, model_id TEXT NOT NULL, rank INTEGER NOT NULL,
  PRIMARY KEY(day, model_id)
);
CREATE TABLE IF NOT EXISTS notified_posts (post_id TEXT PRIMARY KEY, notified_at INTEGER NOT NULL);

INSERT OR IGNORE INTO accounts VALUES
('jiangnan','江南官改','@jiangnan'),('wuxing','五星体育','@wuxing_sports'),
('f1','Formula 1','@F1'),('fia','FIA','@fia'),
('openai','ChatGPT 官方','@OpenAI'),('radar','ChatGPT 雷达','@radar'),
('cn','国内热点','@china_now'),('global','全球热点','@world_now');
INSERT OR IGNORE INTO topics VALUES ('F1','F1'),('AI','AI'),('玩机','玩机'),('国内','国内'),('全球','全球');

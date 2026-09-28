CREATE TABLE IF NOT EXISTS translations (
  cache_key TEXT PRIMARY KEY, body TEXT NOT NULL, created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS translation_budget (
  day TEXT PRIMARY KEY, chunks INTEGER NOT NULL
);

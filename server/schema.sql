-- SimpleRemote anonymous code statistics (Cloudflare D1)
CREATE TABLE IF NOT EXISTS events (
  id     INTEGER PRIMARY KEY AUTOINCREMENT,
  t      TEXT    NOT NULL,          -- "setup" | "deleted"
  cat    TEXT    NOT NULL,          -- device category ("ac", "tv", ...)
  brand  TEXT    NOT NULL,
  code   TEXT    NOT NULL,          -- code-set id in the app's databases
  src    TEXT    NOT NULL,          -- flipper | irext | smartir | esp | github
  ok     INTEGER,                   -- setup: 1 worked, 0 did not
  step   INTEGER,
  mixed  INTEGER NOT NULL DEFAULT 0,
  days   INTEGER,                   -- deleted: days kept
  phone  TEXT    NOT NULL,
  sdk    INTEGER NOT NULL,
  v      TEXT    NOT NULL,
  ts     INTEGER NOT NULL,          -- event time from the phone (unix s)
  received INTEGER NOT NULL         -- server time (unix s)
);
CREATE INDEX IF NOT EXISTS events_code ON events (code);
CREATE INDEX IF NOT EXISTS events_brand ON events (cat, brand);

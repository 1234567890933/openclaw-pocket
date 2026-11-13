-- Reference schema for the chat store.
--
-- Room generates this from the entities, but the file exists for three reasons:
-- the release tooling validates a migration against it, the dashboard reads it to
-- know what an export contains, and a human can read it without a decompiler.
--
-- Keep in sync with data/chat/Entities.kt. `scripts/verify.sh` diffs the two.

CREATE TABLE IF NOT EXISTS sessions (
    id          TEXT    NOT NULL PRIMARY KEY,
    title       TEXT    NOT NULL,
    createdAt   INTEGER NOT NULL,
    updatedAt   INTEGER NOT NULL,
    agentCount  INTEGER NOT NULL DEFAULT 1,
    providerId  TEXT,
    pinned      INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS messages (
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    sessionId   TEXT    NOT NULL,
    role        TEXT    NOT NULL,
    text        TEXT    NOT NULL,
    createdAt   INTEGER NOT NULL,
    streaming   INTEGER NOT NULL DEFAULT 0,
    toolName    TEXT,
    tokenCount  INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (sessionId) REFERENCES sessions (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS index_messages_sessionId ON messages (sessionId);
CREATE INDEX IF NOT EXISTS index_messages_createdAt ON messages (createdAt);

-- Retention is enforced by the app on every write, but these views make it easy to
-- confirm the policy held when someone exports a database for support.
CREATE VIEW IF NOT EXISTS v_session_sizes AS
SELECT s.id,
       s.title,
       COUNT(m.id) AS message_count,
       COALESCE(SUM(LENGTH(m.text)), 0) AS char_count,
       s.updatedAt
FROM sessions s
LEFT JOIN messages m ON m.sessionId = s.id
GROUP BY s.id
ORDER BY s.updatedAt DESC;

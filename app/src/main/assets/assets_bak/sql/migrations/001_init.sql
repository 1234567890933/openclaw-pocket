-- 001: initial chat store.
--
-- Written as a plain migration so a future export/import tool can rebuild a
-- database outside Android, where Room's identity hash check does not apply.

PRAGMA foreign_keys = ON;

CREATE TABLE sessions (
    id          TEXT    NOT NULL PRIMARY KEY,
    title       TEXT    NOT NULL,
    createdAt   INTEGER NOT NULL,
    updatedAt   INTEGER NOT NULL,
    agentCount  INTEGER NOT NULL DEFAULT 1,
    providerId  TEXT,
    pinned      INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE messages (
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

CREATE INDEX index_messages_sessionId ON messages (sessionId);
CREATE INDEX index_messages_createdAt ON messages (createdAt);

package com.dailysatori.service.migration

import app.cash.sqldelight.db.SqlDriver

internal object ArticleAiSchema {
    fun create(driver: SqlDriver) {
        statements.trimIndent().split(';').map(String::trim).filter(String::isNotEmpty).forEach {
            driver.execute(null, it, 0)
        }
    }

    private val statements = """
        CREATE TABLE IF NOT EXISTS article_ai_source (
          article_id INTEGER NOT NULL PRIMARY KEY REFERENCES article(id) ON DELETE CASCADE,
          body_hash TEXT NOT NULL,
          body_kind TEXT NOT NULL,
          upstream_summary TEXT,
          cache_hits INTEGER NOT NULL DEFAULT 0
        );

        CREATE INDEX IF NOT EXISTS idx_article_ai_source_hash ON article_ai_source(body_hash);

        CREATE TABLE IF NOT EXISTS article_ai_result (
          body_hash TEXT NOT NULL,
          kind TEXT NOT NULL,
          version INTEGER NOT NULL,
          target TEXT NOT NULL,
          markdown TEXT NOT NULL,
          partial INTEGER NOT NULL,
          PRIMARY KEY (body_hash, kind, version, target)
        );

        CREATE TABLE IF NOT EXISTS article_ai_job (
          id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          article_id INTEGER NOT NULL,
          body_hash TEXT NOT NULL,
          mode TEXT NOT NULL,
          version INTEGER NOT NULL,
          target TEXT NOT NULL,
          priority INTEGER NOT NULL,
          state TEXT NOT NULL DEFAULT 'queued',
          code TEXT NOT NULL DEFAULT '',
          not_before INTEGER NOT NULL DEFAULT 0,
          requests INTEGER NOT NULL DEFAULT 0,
          cache_hits INTEGER NOT NULL DEFAULT 0
        );

        CREATE INDEX IF NOT EXISTS idx_article_ai_job_pick ON article_ai_job(state, priority, id);
        CREATE INDEX IF NOT EXISTS idx_article_ai_job_hash ON article_ai_job(body_hash, version, target, id);

        CREATE TABLE IF NOT EXISTS article_ai_attempt (
          id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          job_id INTEGER NOT NULL REFERENCES article_ai_job(id) ON DELETE CASCADE,
          started_at INTEGER NOT NULL,
          outcome TEXT NOT NULL DEFAULT 'unknown',
          input_tokens INTEGER,
          output_tokens INTEGER,
          cached_input_tokens INTEGER
        );
    """
}

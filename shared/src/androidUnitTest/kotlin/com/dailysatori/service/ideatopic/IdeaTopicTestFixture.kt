package com.dailysatori.service.ideatopic

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.IdeaTopicRepository
import com.dailysatori.shared.db.DailySatoriDatabase

/** Real in-memory database with a controllable clock and deterministic IDs. */
class IdeaTopicTestFixture {
    val driver: JdbcSqliteDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    val db: DailySatoriDatabase
    val repository: IdeaTopicRepository
    val service: IdeaTopicService
    var clockMs: Long = 1_000L
    private var idSeq: Int = 0

    init {
        DailySatoriDatabase.Schema.create(driver)
        db = DailySatoriDatabase(driver)
        repository = IdeaTopicRepository(db)
        service = IdeaTopicService(repository, now = { clockMs }, newId = { nextId() })
    }

    fun nextId(prefix: String = "gen"): String = "$prefix-${++idSeq}"

    fun createDiary(content: String, createdAt: Long = clockMs, tags: String? = null): Long {
        db.dailySatoriQueries.insertDiary(content, tags, null, null, createdAt, createdAt)
        return lastInsertRowId()
    }

    private fun lastInsertRowId(): Long = driver.executeQuery(
        0,
        "SELECT last_insert_rowid()",
        { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0)!!)
        },
        0,
    ).value

    fun close() {
        driver.close()
    }
}

fun diarySnapshot(
    diaryId: Long,
    title: String = "日记标题",
    content: String = "日记正文",
    createdAt: Long = 1_000L,
    url: String? = null,
): IdeaSourceSnapshot = IdeaSourceSnapshot(
    key = IdeaSourceKey(IdeaSourceTypes.Diary, diaryId.toString()),
    originalTitle = title,
    originalContent = content,
    originalCreatedAt = createdAt,
    originalRecordId = diaryId.toString(),
    originalUrl = url,
)

fun opportunitySnapshot(
    opportunityId: String,
    title: String = "机会标题",
    originalContent: String = "新闻原文",
    url: String? = "https://example.com/a",
    articleId: String? = "42",
    analysisContent: String? = "机会提炼",
    analysisAt: Long? = 2_000L,
    analysisVersion: String? = "v1",
): IdeaSourceSnapshot = IdeaSourceSnapshot(
    key = IdeaSourceKey(IdeaSourceTypes.NewsOpportunity, opportunityId),
    originalTitle = title,
    originalContent = originalContent,
    originalCreatedAt = 1_500L,
    originalRecordId = articleId,
    originalUrl = url,
    analysisId = opportunityId,
    analysisContent = analysisContent,
    analysisCreatedAt = analysisAt,
    analysisVersion = analysisVersion,
)

package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.service.diary.DiaryThreadOverview
import com.dailysatori.service.diary.DiaryThreadSnapshot
import com.dailysatori.service.diary.DiaryThreadSource
import com.dailysatori.service.diary.DiaryThreadSummary
import com.dailysatori.service.diary.DiaryThreadSummaryStatus
import com.dailysatori.service.diary.hasRenderableThreadContent
import com.dailysatori.service.diary.pendingThreadAttachmentCount
import com.dailysatori.service.diary.renderDiaryThreadContent
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Diary
import com.dailysatori.shared.db.Diary_attachment
import com.dailysatori.shared.db.Diary_thread_revision
import com.dailysatori.shared.db.Diary_thread_summary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/**
 * 日记串的唯一写入与读取边界：续写归属、原文版本和带版本的汇总提交都在这里完成。
 * 原文版本由数据库触发器维护，覆盖转写和自动标题等直接 SQL 回写。
 */
class DiaryThreadRepository(
    private val db: DailySatoriDatabase,
    private val driver: SqlDriver,
) {
    private val q get() = db.dailySatoriQueries

    /** 任意记录（主日记或续写）对应的主日记 ID；记录不存在返回 null。 */
    fun rootId(recordId: Long): Long? = q.selectDiaryById(recordId).executeAsOneOrNull()
        ?.let { it.parent_diary_id ?: it.id }

    fun getSnapshot(rootId: Long): DiaryThreadSnapshot? {
        val root = q.selectDiaryById(rootId).executeAsOneOrNull() ?: return null
        if (root.parent_diary_id != null) return null
        val entries = listOf(root) + q.selectDiaryRepliesForRoot(rootId).executeAsList()
        val attachments = q.selectDiaryThreadAttachments(rootId).executeAsList()
        return DiaryThreadSnapshot(
            root = root,
            entries = entries,
            attachments = attachments,
            revision = revisionOf(rootId),
            pendingAttachmentCount = pendingThreadAttachmentCount(attachments),
            summary = q.selectDiaryThreadSummary(rootId).executeAsOneOrNull()?.toSummary(),
        )
    }

    fun observeThread(rootId: Long): Flow<DiaryThreadSnapshot?> = combine(
        q.selectDiaryById(rootId).asFlow().mapToOneOrNull(Dispatchers.IO),
        q.selectDiaryRepliesForRoot(rootId).asFlow().mapToList(Dispatchers.IO),
        q.selectDiaryThreadAttachments(rootId).asFlow().mapToList(Dispatchers.IO),
        q.selectDiaryThreadRevision(rootId).asFlow().mapToOneOrNull(Dispatchers.IO),
        q.selectDiaryThreadSummary(rootId).asFlow().mapToOneOrNull(Dispatchers.IO),
    ) { root, replies, attachments, revision, summary ->
        val main = root?.takeIf { it.parent_diary_id == null } ?: return@combine null
        DiaryThreadSnapshot(
            root = main,
            entries = listOf(main) + replies,
            attachments = attachments,
            revision = revision?.revision ?: 0L,
            pendingAttachmentCount = pendingThreadAttachmentCount(attachments),
            summary = summary?.toSummary(),
        )
    }

    /** 只允许一层续写：父记录必须仍是存在的主日记。 */
    suspend fun createReply(
        rootId: Long,
        content: String,
        mood: String? = null,
        images: String? = null,
    ): Long = q.transactionWithResult {
        val root = q.selectDiaryById(rootId).executeAsOneOrNull()
        require(root != null && root.parent_diary_id == null) {
            "Diary $rootId is not an existing root diary"
        }
        val now = Clock.System.now().toEpochMilliseconds()
        q.insertDiaryReply(content, null, mood, images, now, now, rootId)
        lastInsertRowId()
    }

    /** 只清理没有任何真实正文、图片或附件的续写，避免误删录音成果。 */
    fun discardEmptyReply(replyId: Long): Boolean = q.transactionWithResult {
        val reply = q.selectDiaryById(replyId).executeAsOneOrNull()
        if (reply == null || reply.parent_diary_id == null) return@transactionWithResult false
        if (reply.hasRenderableThreadContent()) return@transactionWithResult false
        if (!reply.images.isNullOrBlank()) return@transactionWithResult false
        if (q.selectAttachmentsForDiary(replyId).executeAsList().isNotEmpty()) return@transactionWithResult false
        q.deleteDiary(replyId)
        true
    }

    fun getSource(rootId: Long): DiaryThreadSource? {
        val root = q.selectDiaryById(rootId).executeAsOneOrNull() ?: return null
        if (root.parent_diary_id != null) return null
        val entries = listOf(root) + q.selectDiaryRepliesForRoot(rootId).executeAsList()
        return DiaryThreadSource(
            rootId = rootId,
            content = renderDiaryThreadContent(entries),
            createdAt = root.created_at,
            updatedAt = root.updated_at,
            revision = revisionOf(rootId),
        )
    }

    private fun sourcesOf(catalog: ThreadCatalog): List<DiaryThreadSource> = catalog.roots.map { root ->
        DiaryThreadSource(
            rootId = root.id,
            content = renderDiaryThreadContent(catalog.entriesByRoot[root.id].orEmpty()),
            createdAt = root.created_at,
            updatedAt = root.updated_at,
            revision = catalog.revisions[root.id] ?: 0L,
        )
    }

    /** 同步读取所有主日记的整串原文，供聊天上下文等非 Flow 调用。 */
    fun sources(): List<DiaryThreadSource> = sourcesOf(readCatalog())

    fun observeSources(): Flow<List<DiaryThreadSource>> = observeCatalog().map { catalog -> sourcesOf(catalog) }

    fun observeOverviews(): Flow<List<DiaryThreadOverview>> = observeCatalog().map { catalog ->
        catalog.roots.map { root ->
            DiaryThreadOverview(
                rootId = root.id,
                replyCount = (catalog.entriesByRoot[root.id]?.size ?: 1).toLong() - 1L,
                pendingAttachmentCount = pendingThreadAttachmentCount(catalog.attachmentsByRoot[root.id].orEmpty()),
                summary = catalog.summaries[root.id],
            )
        }
    }

    /** 同版本条件写入：主日记不存在、已被删除或原文版本已前进时都拒绝。 */
    fun commitSummary(rootId: Long, revision: Long, text: String): Boolean = q.transactionWithResult {
        if (!acceptsRevision(rootId, revision)) return@transactionWithResult false
        val now = Clock.System.now().toEpochMilliseconds()
        q.upsertDiaryThreadSummary(
            diary_id = rootId,
            source_revision = revision,
            summary_revision = revision,
            summary = text,
            status = DiaryThreadSummaryStatus.ready,
            error_message = "",
            generated_at = now,
            updated_at = now,
        )
        true
    }

    /** 状态写入同样绑定原文版本，旧任务不得改写新版本的状态。 */
    fun markSummaryState(
        rootId: Long,
        revision: Long,
        status: String,
        error: String? = null,
    ): Boolean = q.transactionWithResult {
        if (!acceptsRevision(rootId, revision)) return@transactionWithResult false
        val existing = q.selectDiaryThreadSummary(rootId).executeAsOneOrNull()
        val now = Clock.System.now().toEpochMilliseconds()
        q.upsertDiaryThreadSummary(
            diary_id = rootId,
            source_revision = revision,
            summary_revision = existing?.summary_revision ?: 0L,
            summary = existing?.summary.orEmpty(),
            status = status,
            error_message = error.orEmpty(),
            generated_at = existing?.generated_at,
            updated_at = now,
        )
        true
    }

    /**
     * 启动恢复入口：有续写且待汇总（缺失、版本过期、仍在生成）的主日记。
     * 与当前版本一致且已终态失败的不返回，避免自动重排失败任务。
     */
    fun pendingSummaryRootIds(): List<Long> = q.transactionWithResult {
        val catalog = readCatalog()
        catalog.roots.mapNotNull { root ->
            if ((catalog.entriesByRoot[root.id]?.size ?: 1) <= 1) return@mapNotNull null
            val summary = catalog.summaries[root.id]
            val needs = summary == null ||
                summary.sourceRevision != (catalog.revisions[root.id] ?: 0L) ||
                summary.status == DiaryThreadSummaryStatus.pending ||
                summary.status == DiaryThreadSummaryStatus.running
            root.id.takeIf { needs }
        }
    }

    private fun acceptsRevision(rootId: Long, revision: Long): Boolean {
        val root = q.selectDiaryById(rootId).executeAsOneOrNull() ?: return false
        if (root.parent_diary_id != null) return false
        return revisionOf(rootId) == revision
    }

    private fun revisionOf(rootId: Long): Long =
        q.selectDiaryThreadRevision(rootId).executeAsOneOrNull()?.revision ?: 0L

    private fun observeCatalog(): Flow<ThreadCatalog> = combine(
        q.selectAllDiaries().asFlow().mapToList(Dispatchers.IO),
        q.selectAllDiaryAttachments().asFlow().mapToList(Dispatchers.IO),
        q.selectAllDiaryThreadRevisions().asFlow().mapToList(Dispatchers.IO),
        q.selectAllDiaryThreadSummaries().asFlow().mapToList(Dispatchers.IO),
    ) { diaries, attachments, revisions, summaries ->
        ThreadCatalog.from(diaries, attachments, revisions, summaries)
    }

    private fun readCatalog(): ThreadCatalog = ThreadCatalog.from(
        q.selectAllDiaries().executeAsList(),
        q.selectAllDiaryAttachments().executeAsList(),
        q.selectAllDiaryThreadRevisions().executeAsList(),
        q.selectAllDiaryThreadSummaries().executeAsList(),
    )

    private fun lastInsertRowId(): Long =
        driver.executeQuery(0, "SELECT last_insert_rowid()", { cursor ->
            check(cursor.next().value) { "last_insert_rowid() returned no row" }
            QueryResult.Value(checkNotNull(cursor.getLong(0)) { "last_insert_rowid() was null" })
        }, 0).value
}

private class ThreadCatalog(
    val roots: List<Diary>,
    val entriesByRoot: Map<Long, List<Diary>>,
    val attachmentsByRoot: Map<Long, List<Diary_attachment>>,
    val revisions: Map<Long, Long>,
    val summaries: Map<Long, DiaryThreadSummary>,
) {
    companion object {
        fun from(
            diaries: List<Diary>,
            attachments: List<Diary_attachment>,
            revisions: List<Diary_thread_revision>,
            summaries: List<Diary_thread_summary>,
        ): ThreadCatalog {
            val roots = diaries.filter { it.parent_diary_id == null }.sortedByDescending { it.created_at }
            val repliesByRoot = diaries.filter { it.parent_diary_id != null }.groupBy { it.parent_diary_id!! }
            val rootByRecord = diaries.associate { it.id to (it.parent_diary_id ?: it.id) }
            return ThreadCatalog(
                roots = roots,
                entriesByRoot = roots.associate { root ->
                    root.id to (
                        listOf(root) + repliesByRoot[root.id].orEmpty()
                            .sortedWith(compareBy({ it.created_at }, { it.id }))
                        )
                },
                attachmentsByRoot = attachments.groupBy { rootByRecord[it.diary_id] ?: it.diary_id },
                revisions = revisions.associate { it.root_diary_id to it.revision },
                summaries = summaries.associate { it.diary_id to it.toSummary() },
            )
        }
    }
}

private fun Diary_thread_summary.toSummary(): DiaryThreadSummary = DiaryThreadSummary(
    text = summary,
    sourceRevision = source_revision,
    summaryRevision = summary_revision,
    status = status,
    errorMessage = error_message.ifBlank { null },
    generatedAt = generated_at,
)

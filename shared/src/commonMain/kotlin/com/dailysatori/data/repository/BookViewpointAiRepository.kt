package com.dailysatori.data.repository

import com.dailysatori.shared.db.Book_viewpoint_ai_message
import com.dailysatori.shared.db.Book_viewpoint_ai_session
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.datetime.Clock

class BookViewpointAiRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getSessionsByViewpoint(viewpointId: Long): List<Book_viewpoint_ai_session> =
        q.selectBookReflectionSessionsByViewpoint(viewpointId).executeAsList()

    fun getLastOpenedSession(viewpointId: Long): Book_viewpoint_ai_session? =
        q.selectLastOpenedBookReflectionSession(viewpointId).executeAsOneOrNull()

    fun getLatestUnsummarizedSession(viewpointId: Long): Book_viewpoint_ai_session? =
        q.selectLatestUnsummarizedBookReflectionSession(viewpointId).executeAsOneOrNull()

    fun getSessionById(sessionId: Long): Book_viewpoint_ai_session? =
        q.selectBookReflectionSessionById(sessionId).executeAsOneOrNull()

    fun createSession(viewpointId: Long, title: String = "新的思考", now: Long = now()): Long {
        q.insertBookReflectionSession(
            viewpoint_id = viewpointId,
            title = title,
            summary = "",
            summary_status = "none",
            summary_error = "",
            created_at = now,
            updated_at = now,
            last_opened_at = now,
            summarized_at = null,
        )
        return q.selectLastInsertedBookReflectionSessionId().executeAsOne()
    }

    fun markOpened(sessionId: Long, now: Long = now()) {
        q.markBookReflectionSessionOpened(last_opened_at = now, updated_at = now, id = sessionId)
    }

    fun updateTitle(sessionId: Long, title: String, now: Long = now()) {
        q.updateBookReflectionSessionTitle(title = title, updated_at = now, id = sessionId)
    }

    fun updateSummaryStatus(sessionId: Long, status: String, error: String = "", now: Long = now()) {
        q.updateBookReflectionSummaryStatus(summary_status = status, summary_error = error, updated_at = now, id = sessionId)
    }

    fun updateSummary(sessionId: Long, title: String, summary: String, now: Long = now()) {
        q.updateBookReflectionSummary(
            title = title,
            summary = summary,
            summary_status = "ready",
            summarized_at = now,
            updated_at = now,
            id = sessionId,
        )
    }

    fun deleteSession(sessionId: Long) = q.deleteBookReflectionSession(sessionId)

    fun getMessagesBySession(sessionId: Long): List<Book_viewpoint_ai_message> =
        q.selectBookReflectionMessagesBySession(sessionId).executeAsList()

    fun insertMessage(
        sessionId: Long,
        role: String,
        content: String,
        status: String = "ready",
        errorMessage: String = "",
        now: Long = now(),
    ): Long {
        q.insertBookReflectionMessage(sessionId, role, content, status, errorMessage, now)
        return q.selectLastInsertedBookReflectionMessageId().executeAsOne()
    }

    fun updateMessage(messageId: Long, content: String, status: String, errorMessage: String = "") {
        q.updateBookReflectionMessage(content = content, status = status, error_message = errorMessage, id = messageId)
    }

    fun deleteMessagesBySession(sessionId: Long) = q.deleteBookReflectionMessagesBySession(sessionId)

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
}

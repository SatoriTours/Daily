package com.dailysatori.data.repository

import com.dailysatori.shared.db.Chat_conversation
import com.dailysatori.shared.db.DailySatoriDatabase

class ChatConversationRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getBySession(sessionId: String): List<Chat_conversation> =
        q.selectChatBySession(sessionId).executeAsList()

    fun getLatestBySession(sessionId: String, limit: Long): List<Chat_conversation> =
        q.selectLatestChatBySession(sessionId, limit).executeAsList().asReversed()

    fun getBefore(sessionId: String, beforeCreatedAt: Long, limit: Long): List<Chat_conversation> =
        q.selectChatBefore(sessionId, beforeCreatedAt, limit).executeAsList().asReversed()

    fun getSessions(): List<String> =
        q.selectChatSessions().executeAsList()

    fun insert(
        sessionId: String,
        role: String,
        content: String,
        searchResults: String? = null,
        steps: String? = null,
        createdAt: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
    ) {
        q.insertChat(sessionId, role, content, searchResults, steps, createdAt)
    }

    fun deleteBySession(sessionId: String) =
        q.deleteChatBySession(sessionId)

    fun deleteMessage(sessionId: String, role: String, content: String, createdAt: Long) =
        q.deleteChatMessage(sessionId, role, content, createdAt)
}

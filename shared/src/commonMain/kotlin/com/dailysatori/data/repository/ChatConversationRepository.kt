package com.dailysatori.data.repository

import com.dailysatori.shared.db.Chat_conversation
import com.dailysatori.shared.db.DailySatoriDatabase

class ChatConversationRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getBySession(sessionId: String): List<Chat_conversation> =
        q.selectChatBySession(sessionId).executeAsList()

    fun getSessions(): List<String> =
        q.selectChatSessions().executeAsList()

    fun insert(
        sessionId: String,
        role: String,
        content: String,
        searchResults: String? = null,
        steps: String? = null,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertChat(sessionId, role, content, searchResults, steps, now)
    }

    fun deleteBySession(sessionId: String) =
        q.deleteChatBySession(sessionId)
}

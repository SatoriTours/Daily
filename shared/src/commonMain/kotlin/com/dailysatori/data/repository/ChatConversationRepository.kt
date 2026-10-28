package com.dailysatori.data.repository

import com.dailysatori.shared.db.Chat_conversation
import com.dailysatori.shared.db.DailySatoriDatabase

class ChatConversationRepository(private val db: DailySatoriDatabase,private val privacy:com.dailysatori.service.assistant.AssistantPrivacyRepository?=null) {
    private val q get() = db.dailySatoriQueries

    fun getById(id: Long): Chat_conversation? = q.selectChatById(id).executeAsOneOrNull()

    fun getBySession(sessionId: String): List<Chat_conversation> =
        q.selectChatBySession(sessionId).executeAsList()

    fun getLatestBySession(sessionId: String, limit: Long): List<Chat_conversation> =
        q.selectLatestChatBySession(sessionId, limit).executeAsList().asReversed()

    fun getBefore(sessionId: String, beforeCreatedAt: Long, limit: Long): List<Chat_conversation> =
        q.selectChatBefore(sessionId, beforeCreatedAt, Long.MIN_VALUE,limit).executeAsList().asReversed()

    fun getBefore(sessionId:String,beforeCreatedAt:Long,beforeId:Long,limit:Long):List<Chat_conversation> =
        q.selectChatBefore(sessionId,beforeCreatedAt,beforeId,limit).executeAsList().asReversed()

    fun getSessions(): List<String> =
        q.selectChatSessions().executeAsList()

    fun insert(
        sessionId: String,
        role: String,
        content: String,
        searchResults: String? = null,
        steps: String? = null,
        createdAt: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
    ) = q.transaction {
        require(sessionId.isNotBlank())
        q.ensureChatSession(sessionId,createdAt)
        q.insertChat(sessionId, role, content, searchResults, steps, createdAt)
    }

    /** Accept before starting any model/configuration/network work. Failure must reach the caller. */
    fun acceptUserInput(sessionId: String, content: String): Long = q.transactionWithResult {
        require(sessionId.isNotBlank() && sessionId.length <= 128 && content.isNotBlank())
        insert(sessionId, "user", content)
        q.selectLastInsertedChatId().executeAsOne()
    }

    fun deleteMessage(id:Long) = checkNotNull(privacy) { "Privacy deletion owner required" }.deleteConversationMessage(id)

    fun deleteBySession(sessionId: String) = q.transaction {
        getBySession(sessionId).forEach { deleteMessage(it.id) }
    }

    fun deleteMessage(sessionId: String, role: String, content: String, createdAt: Long) {
        val matches=getBySession(sessionId).filter { it.role==role && it.content==content && it.created_at==createdAt }
        require(matches.size<=1) { "Ambiguous message identity; delete by ID" }
        matches.singleOrNull()?.let { deleteMessage(it.id) }
    }
}

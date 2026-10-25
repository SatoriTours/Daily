package com.dailysatori.service.assistant

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AssistantReference(val source: String, val id: String, val title: String)
data class AssistantActionView(
    val id: String, val kind: String, val status: String,
    val targetId: String? = null, val taskId: Long? = null, val message: String = "",
)
data class AssistantMessage(
    val id: Long, val role: String, val content: String, val createdAt: Long,
    val turnId: Long? = null, val taskId: Long? = null, val status: String = "complete",
    val error: String = "", val excluded: Boolean = false,
    val references: List<AssistantReference> = emptyList(),
    val actions: List<AssistantActionView> = emptyList(),
)
data class AssistantMemoryView(
    val id: Long, val content: String, val kind: String, val source: String,
    val sourceId: String, val active: Boolean, val evidence: String = "",
)

data class AssistantSessionSummary(
    val id: String, val title: String, val preview: String, val updatedAt: Long,
)

/** Host-owned commands. No model/network work is tied to the screen's coroutine lifetime. */
interface AssistantConversation {
    fun sessions(): Flow<List<String>>
    fun sessionSummaries(): Flow<List<AssistantSessionSummary>> = sessions().map { ids ->
        ids.map { AssistantSessionSummary(it, "", "", 0) }
    }
    fun messages(sessionId: String): Flow<List<AssistantMessage>>
    fun memories(): Flow<List<AssistantMemoryView>>
    fun cloudConsent(): Boolean
    fun setCloudConsent(accepted: Boolean)
    fun send(sessionId: String, clientMessageId: String, content: String): AssistantTurn
    fun sendWithContext(sessionId: String, clientMessageId: String, content: String,
        explicitContext: com.dailysatori.service.diary.DiaryThoughtChatContext?, includeThoughts: Boolean): AssistantTurn =
        send(sessionId, clientMessageId, content)
    fun cancel(turnId: Long)
    fun retry(turnId: Long)
    fun excludeMessage(messageId: Long)
    fun deleteMessage(messageId: Long)
    fun deleteSession(sessionId: String)
    fun forgetMemory(memoryId: Long)
    fun correctMemory(memoryId: Long, corrected: String)
}

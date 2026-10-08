package com.dailysatori.service.ideatopic

import com.dailysatori.service.diagnostics.DiagnosticLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Network orchestration; TopicService alone owns writes, jobs and request state. */
class IdeaTopicAiWorkflow(
    private val service: IdeaTopicService,
    private val port: IdeaTopicAiPort,
    private val newId: () -> String = { DiagnosticLog.newId() },
) {
    suspend fun send(sessionId: String, text: String, onSaved: () -> Unit = {}) {
        val question = text.trim()
        if (question.isBlank()) throw IdeaTopicException(IdeaTopicError.InvalidInput)
        if (question.length > IdeaAiMaxUserPromptCharacters) throw IdeaTopicException(IdeaTopicError.InputTooLong)
        requestReply(sessionId, question, newId(), newId(), retry = false, onSaved = onSaved)
    }

    suspend fun retry(sessionId: String, replyMessageId: String) {
        val messages = service.messagesSync(sessionId)
        val index = messages.indexOfFirst { it.id == replyMessageId && it.role == IdeaMessageRoles.Assistant }
        val feedback = if (index >= 0) messages.take(index).lastOrNull { it.role == IdeaMessageRoles.User } else null
        if (feedback == null || messages.drop(index + 1).any { it.role == IdeaMessageRoles.User }) {
            throw IdeaTopicException(IdeaTopicError.InvalidInput)
        }
        requestReply(sessionId, feedback.content, feedback.id, replyMessageId, retry = true)
    }

    private suspend fun requestReply(
        sessionId: String, question: String, userMessageId: String, replyMessageId: String,
        retry: Boolean, onSaved: () -> Unit = {},
    ) {
        val session = service.sessionOrThrow(sessionId)
        val main = service.resolveTopicId(session.topicId) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        val token = newId()
        service.beginRequest(main, token)
        var replyPrepared = false
        try {
            if (retry) service.resetReplyForRetry(sessionId, replyMessageId) else {
                service.saveUserMessage(sessionId, userMessageId, question)
                onSaved()
                service.savePendingReply(sessionId, replyMessageId)
            }
            replyPrepared = true
            val detail = service.getDetailSync(main) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
            val history = service.messagesSync(sessionId).takeWhile { it.id != userMessageId }
            val context = buildIdeaAiContext(detail, sessionId, history, question)
            val buffer = StringBuilder()
            val reply = port.reply(context) { chunk ->
                buffer.append(chunk)
                service.updateReplyContent(main, token, replyMessageId, buffer.toString())
            }
            service.completeReply(main, token, replyMessageId, reply)
        } catch (cancellation: CancellationException) {
            if (replyPrepared) withContext(NonCancellable) { service.interruptReply(main, token, replyMessageId) }
            throw cancellation
        } catch (failure: Exception) {
            val code = (failure as? IdeaTopicException)?.code?.name ?: IdeaTopicError.StorageFailure.name
            if (replyPrepared) withContext(NonCancellable) { service.failReply(main, token, replyMessageId, code) }
            throw failure
        } finally {
            withContext(NonCancellable) { service.releaseRequest(main, token) }
        }
    }

    suspend fun summarize(sessionId: String) {
        val session = service.sessionOrThrow(sessionId)
        val main = service.resolveTopicId(session.topicId) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        val completed = service.messagesSync(sessionId).filter { it.status == IdeaMessageStatus.Complete }
        if (completed.isEmpty()) throw IdeaTopicException(IdeaTopicError.InvalidInput)
        val token = newId()
        service.beginRequest(main, token)
        try {
            service.markSessionSummaryPending(sessionId)
            val detail = service.getDetailSync(main) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
            val context = buildIdeaAiContext(detail, sessionId, completed, ideaSummaryInstruction())
            val summary = port.summarize(context)
            service.saveSessionSummary(
                sessionId, token, main, summary,
                coveredMessageIds = context.messages.map { it.id },
                throughMessageId = context.messages.lastOrNull()?.id,
            )
        } catch (failure: Exception) {
            withContext(NonCancellable) { service.failSessionSummary(main, token, sessionId) }
            throw failure
        } finally {
            withContext(NonCancellable) { service.releaseRequest(main, token) }
        }
    }

    suspend fun propose(topicId: String): IdeaTopicDraft {
        val main = service.resolveTopicId(topicId) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        val token = newId()
        val revision = service.beginRequest(main, token)
        try {
            val detail = service.getDetailSync(main) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
            val context = buildIdeaAiContext(detail, null, emptyList(), ideaDraftInstruction())
            val proposal = port.propose(context)
            if (proposal.content.title.trim().isBlank() || proposal.referenceIds.any { it !in context.allowedReferenceIds }) {
                throw IdeaTopicException(IdeaTopicError.InvalidAiResponse)
            }
            return service.saveDraft(main, token, newId(), revision, proposal)
        } finally {
            withContext(NonCancellable) { service.releaseRequest(main, token) }
        }
    }

    fun isBusy(topicId: String): Boolean = service.isRequestActive(topicId)

    suspend fun cancel(topicId: String) {
        val main = service.resolveTopicId(topicId) ?: return
        service.forceCancelRequest(main)
    }

    suspend fun recoverInterruptedRequests() { service.recoverInterruptedRequests() }
}

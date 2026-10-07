package com.dailysatori.service.ideatopic

import com.dailysatori.service.diagnostics.DiagnosticLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coordinates one AI request at a time per main topic. Writes stay behind [IdeaTopicService]:
 * this class only builds context, calls the network port and reports request lifecycle events.
 */
class IdeaTopicAiWorkflow(
    private val service: IdeaTopicService,
    private val port: IdeaTopicAiPort,
    private val newId: () -> String = { DiagnosticLog.newId() },
) {
    private val jobs = mutableMapOf<String, Job>()
    private val jobMutex = Mutex()

    /** Sends one user message in [sessionId] and stores the streaming reply. */
    suspend fun send(sessionId: String, text: String) {
        val question = text.trim()
        if (question.isBlank()) throw IdeaTopicException(IdeaTopicError.InvalidInput)
        if (question.length > IdeaAiMaxUserPromptCharacters) throw IdeaTopicException(IdeaTopicError.InputTooLong)

        val session = service.sessionOrThrow(sessionId)
        val mainTopicId = service.resolveTopicId(session.topicId)
            ?: throw IdeaTopicException(IdeaTopicError.NotFound)

        val token = newId()
        val userMessageId = newId()
        val replyMessageId = newId()
        service.beginRequest(mainTopicId, token)
        registerJob(mainTopicId)
        try {
            service.saveUserMessage(sessionId, userMessageId, question)
            service.savePendingReply(sessionId, replyMessageId)
            val detail = service.getDetailSync(mainTopicId) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
            val history = service.messagesSync(sessionId).filter { it.id != userMessageId }
            val context = buildIdeaAiContext(detail, sessionId, history, question)
            val buffer = StringBuilder()
            val reply = port.reply(context) { chunk ->
                buffer.append(chunk)
                service.updateReplyContent(replyMessageId, buffer.toString())
            }
            service.completeReply(mainTopicId, token, replyMessageId, reply)
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { service.interruptReply(mainTopicId, token, replyMessageId) }
            throw cancellation
        } catch (failure: Exception) {
            val code = (failure as? IdeaTopicException)?.code?.name ?: IdeaTopicError.StorageFailure.name
            withContext(NonCancellable) { service.failReply(mainTopicId, token, replyMessageId, code) }
            throw failure
        } finally {
            unregisterJob(mainTopicId)
        }
    }

    /** Summarizes the messages actually included in the built context and records their coverage. */
    suspend fun summarize(sessionId: String) {
        val session = service.sessionOrThrow(sessionId)
        val mainTopicId = service.resolveTopicId(session.topicId)
            ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        val completed = service.messagesSync(sessionId).filter { it.status == IdeaMessageStatus.Complete }
        if (completed.isEmpty()) throw IdeaTopicException(IdeaTopicError.InvalidInput)

        val token = newId()
        service.beginRequest(mainTopicId, token)
        try {
            service.markSessionSummaryPending(sessionId)
            val detail = service.getDetailSync(mainTopicId) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
            val context = buildIdeaAiContext(detail, sessionId, completed, ideaSummaryInstruction())
            val summary = port.summarize(context)
            service.saveSessionSummary(
                sessionId = sessionId,
                token = token,
                mainTopicId = mainTopicId,
                summary = summary,
                coveredMessageIds = context.messages.map { it.id },
                throughMessageId = context.messages.lastOrNull()?.id,
            )
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { service.releaseRequest(mainTopicId, token) }
            throw cancellation
        } catch (failure: Exception) {
            withContext(NonCancellable) { service.failSessionSummary(mainTopicId, token, sessionId) }
            throw failure
        }
    }

    /** Cancels the in-flight request of the topic (chat or summary) and marks partial output interrupted. */
    suspend fun cancel(topicId: String) {
        val mainTopicId = service.resolveTopicId(topicId) ?: return
        val job = jobMutex.withLock { jobs[mainTopicId] }
        job?.cancel()
        service.forceCancelRequest(mainTopicId)
    }

    /** Marks replies left pending by a previous process as interrupted; never retries them. */
    suspend fun recoverInterruptedRequests() {
        service.recoverInterruptedRequests()
    }

    private suspend fun registerJob(mainTopicId: String) {
        val job = currentCoroutineContext()[Job] ?: return
        jobMutex.withLock { jobs[mainTopicId] = job }
    }

    private suspend fun unregisterJob(mainTopicId: String) {
        jobMutex.withLock { jobs.remove(mainTopicId) }
    }
}

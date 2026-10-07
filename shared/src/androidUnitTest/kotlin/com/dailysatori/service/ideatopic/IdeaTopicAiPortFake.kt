package com.dailysatori.service.ideatopic

import kotlinx.coroutines.CompletableDeferred

/** Controllable in-memory AI port for conversation and draft tests. */
class IdeaTopicAiPortFake : IdeaTopicAiPort {
    var replyResult: String = "AI 回复"
    var summarizeResult: String = "AI 摘要"
    var replyGate: CompletableDeferred<Unit>? = null
    var summarizeGate: CompletableDeferred<Unit>? = null
    var failure: IdeaTopicException? = null
    var lastReplyContext: IdeaAiContext? = null
    var lastSummaryContext: IdeaAiContext? = null

    override suspend fun reply(context: IdeaAiContext, onChunk: suspend (String) -> Unit): String {
        lastReplyContext = context
        onChunk("（部分）")
        replyGate?.await()
        failure?.let { throw it }
        if (replyResult.isBlank()) throw IdeaTopicException(IdeaTopicError.InvalidAiResponse)
        onChunk(replyResult)
        return replyResult
    }

    override suspend fun summarize(context: IdeaAiContext): String {
        lastSummaryContext = context
        summarizeGate?.await()
        failure?.let { throw it }
        if (summarizeResult.isBlank()) throw IdeaTopicException(IdeaTopicError.InvalidAiResponse)
        return summarizeResult
    }
}

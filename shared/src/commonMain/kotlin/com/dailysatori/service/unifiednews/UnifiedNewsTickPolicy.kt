package com.dailysatori.service.unifiednews

enum class UnifiedNewsTickDecision { GENERATE, SKIP_STALE, SKIP_GENERATED }

/**
 * One tick identity travels from scheduling into the queue payload and is re-evaluated at
 * execution time. Only the newest due tick may generate, a tick that already produced a summary is
 * skipped, and an explicit manual refresh is never blocked by automatic de-duplication.
 */
fun unifiedNewsTickDecision(
    tickDueAtMs: Long?,
    latestDueAtMs: Long,
    latestSuccessfulAtMs: Long?,
    manual: Boolean = false,
): UnifiedNewsTickDecision {
    if (manual) return UnifiedNewsTickDecision.GENERATE
    if (tickDueAtMs == null) return UnifiedNewsTickDecision.GENERATE
    if (tickDueAtMs < latestDueAtMs) return UnifiedNewsTickDecision.SKIP_STALE
    if (latestSuccessfulAtMs != null && latestSuccessfulAtMs >= tickDueAtMs) {
        return UnifiedNewsTickDecision.SKIP_GENERATED
    }
    return UnifiedNewsTickDecision.GENERATE
}

package com.dailysatori.service.unifiednews

enum class UnifiedNewsWindowKey(val value: String, val hour: Int, val minute: Int) {
    DAILY("daily", 0, 0),
    W0800("0800", 8, 0),
    W1330("1330", 13, 30),
    W1800("1800", 18, 0),
    W2100("2100", 21, 0),
    FINAL("final", 0, 0),
}

enum class UnifiedNewsSourceType(val dbValue: String, val prefix: String) {
    REMOTE_ARTICLE("remote_article", "R"),
    LOCAL_FAVORITE("local_favorite", "F"),
}

enum class UnifiedNewsSummaryStatus(val value: String) {
    PENDING("pending"),
    SUCCESS("success"),
    FAILED("failed"),
    EMPTY("empty"),
}

data class UnifiedNewsWindow(
    val key: UnifiedNewsWindowKey,
    val summaryDate: String,
    val startMs: Long,
    val endMs: Long,
)

data class NextUnifiedNewsWindow(
    val key: UnifiedNewsWindowKey,
    val dueAt: kotlinx.datetime.Instant,
)

data class UnifiedNewsSourceItem(
    val refKey: String,
    val sourceType: UnifiedNewsSourceType,
    val sourceId: Long? = null,
    val sourceFilename: String? = null,
    val sourceUrl: String? = null,
    val title: String,
    val summary: String,
    val sourceTime: Long? = null,
    val content: String = "",
)

data class UnifiedNewsGenerationResult(
    val success: Boolean,
    val status: UnifiedNewsSummaryStatus,
    val message: String? = null,
)

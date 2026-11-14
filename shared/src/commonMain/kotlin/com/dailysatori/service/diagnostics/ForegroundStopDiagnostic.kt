package com.dailysatori.service.diagnostics

/**
 * 只从已知的 ForegroundServiceDidNotStopInTimeException 提取**固定白名单值**。
 *
 * 绝不保留原始异常消息、未知组件名或包外文本：无法识别的组件/类型一律记为 unknown。
 * 该结果只是“下次崩溃时可用于定位的静态属性”，不构成崩溃原因结论。
 */
object ForegroundStopDiagnostic {
    private const val TARGET_EXCEPTION = "ForegroundServiceDidNotStopInTimeException"

    private val components = linkedMapOf(
        "androidx.work.impl.foreground.SystemForegroundService" to "workmanager_system_foreground",
        "com.dailysatori.core.recording.DiaryRecordingService" to "diary_recording",
    )

    private val typeTokens = linkedMapOf(
        "dataSync" to "data_sync",
        "DATA_SYNC" to "data_sync",
        "microphone" to "microphone",
        "MICROPHONE" to "microphone",
        "camera" to "camera",
        "CAMERA" to "camera",
        "location" to "location",
        "LOCATION" to "location",
        "mediaPlayback" to "media_playback",
        "MEDIA_PLAYBACK" to "media_playback",
        "connectedDevice" to "connectivity",
        "CONNECTED_DEVICE" to "connectivity",
        "shortService" to "short_service",
        "SHORT_SERVICE" to "short_service",
        "specialUse" to "special_use",
        "SPECIAL_USE" to "special_use",
    )

    fun attributes(exceptionClass: String, message: String?): Map<String, String> {
        if (!exceptionClass.endsWith(TARGET_EXCEPTION)) return emptyMap()
        val text = message.orEmpty()
        return mapOf(
            "crashKind" to "fgs_stop_timeout",
            "fgsComponent" to (components.entries.firstOrNull { it.key in text }?.value ?: "unknown"),
            "fgsTypeName" to (typeTokens.entries.firstOrNull { it.key in text }?.value ?: "unknown"),
        )
    }
}

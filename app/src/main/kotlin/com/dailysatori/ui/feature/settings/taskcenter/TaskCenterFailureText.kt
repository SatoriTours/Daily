package com.dailysatori.ui.feature.settings.taskcenter

internal fun taskCenterFailureText(code: String, message: String, translate: (String) -> String): String {
    val category = when {
        code == "opportunity_context_changed" || message == "关注点或思想已更新，请重新分析" -> "context_changed"
        code.contains("auth") -> "auth"
        code.contains("config") || code == "handler_missing" -> "config"
        code.contains("model_unsupported") -> "model"
        code == "rate_limited" -> "rate_limit"
        code.contains("timeout") || message.contains("超时") -> "timeout"
        code == "interrupted" -> "interrupted"
        code == "opportunity_invalid_response" || code == "opportunity_invalid_quote" -> "ai_response"
        code.contains("missing") || code.contains("invalid") -> "input"
        else -> "general"
    }
    val reason = message.trim().ifBlank { translate("attention.task_unknown_error") }
    return "$reason\n\n${translate("task_failure.$category")}\n${translate("task_failure.acknowledge_hint")}"
}

package com.dailysatori.service.skill

object BuiltInSkillTemplates {
    const val weRead = "weread"
}

fun builtInWeReadSkillName(): String = "微信读书"

fun builtInWeReadDescription(): String = "微信读书 Skill，用于搜索书籍、获取图书信息、目录和书评。"

fun builtInWeReadGatewayUrl(): String = "https://i.weread.qq.com/api/agent/gateway"

fun builtInWeReadSkillVersion(): String = "1.0.3"

fun skillSettingsTitle(): String = "Skills"

fun skillAddActionText(): String = "添加 Skill"

fun skillTokenStatus(apiToken: String): String = if (apiToken.trim().isBlank()) "缺少 Token" else "已配置 Token"

fun skillEnabledStatus(enabled: Long): String = if (enabled == 1L) "已启用" else "未启用"

fun skillBuiltinBadge(builtin: Long): String = if (builtin == 1L) "内置" else "自定义"

fun canDeleteSkill(builtin: Long): Boolean = builtin == 0L

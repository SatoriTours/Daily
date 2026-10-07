package com.dailysatori.ui.feature.settings

internal enum class SettingsPage {
    MAIN, AI_CONFIG, AI_PURPOSE, SPEECH, DIARY_TAGS, MCP_SERVER, PLUGIN_CENTER,
    BACKUP_SETTINGS, BACKUP_RESTORE, DATA_IMPORT, SKILLS, DIAGNOSTICS,
    REMINDERS, SMS_REMINDERS, PHONE_ASSISTANT, BOOKKEEPING,
    REMOTE_NEWS, EXTERNAL_FAVORITES, PRIVACY, WEB_SERVICE, UPDATES,
}

internal data class SettingsDestination(val page: SettingsPage, val section: String? = null)

internal data class SettingsEntry(
    val group: String,
    val title: String,
    val description: String,
    val destination: SettingsDestination,
    val keywords: String = "",
)

private fun entry(group: String, title: String, page: SettingsPage, keywords: String = "", section: String? = null) =
    SettingsEntry("settings_design.$group", "settings_design.$title", "settings_design.${title}_hint",
        SettingsDestination(page, section), keywords)

internal val settingsHomeEntries = listOf(
    entry("daily", "reminders", SettingsPage.REMINDERS, "提醒 通知 声音 振动 勿扰 reminder notification sound vibration quiet"),
    entry("daily", "phone", SettingsPage.PHONE_ASSISTANT, "手机助手 短信 通知 记账 权限 sms phone notification ledger"),
    entry("daily", "tags", SettingsPage.DIARY_TAGS, "日记 标签 同义词 diary tags synonyms"),
    entry("capabilities", "models", SettingsPage.AI_CONFIG, "模型 AI 默认 服务商 API Key model provider default"),
    entry("capabilities", "skills", SettingsPage.SKILLS, "提示词 技能 插件 prompt skills plugins"),
    entry("capabilities", "tools", SettingsPage.MCP_SERVER, "外部工具 工具 连接 MCP tools connection"),
    entry("data", "sources", SettingsPage.REMOTE_NEWS, "内容 来源 新闻 收藏 news source favorites"),
    entry("data", "storage", SettingsPage.BACKUP_SETTINGS, "数据 管理 备份 backup data storage"),
    entry("data", "privacy", SettingsPage.PRIVACY, "隐私 privacy"),
    entry("application", "updates", SettingsPage.UPDATES, "更新 关于 版本 渠道 update about version channel"),
    entry("application", "diagnostics", SettingsPage.DIAGNOSTICS, "诊断 日志 错误 diagnostics logs errors"),
)

private val settingsSearchEntries = settingsHomeEntries + listOf(
    entry("daily", "sms", SettingsPage.PHONE_ASSISTANT, "短信权限 sms permission restricted", "sms"),
    entry("daily", "notification_access", SettingsPage.PHONE_ASSISTANT, "通知权限 应用选择 来源 notification access apps", "notification"),
    entry("daily", "reminder_access", SettingsPage.REMINDERS, "定时权限 准时提醒 exact alarm permission", "permissions"),
    entry("capabilities", "speech", SettingsPage.SPEECH, "语音 转写 录音 speech transcription audio API Key"),
    entry("capabilities", "ai_purpose", SettingsPage.AI_PURPOSE, "功能 模型 分配 快速 经济 深度 交互 后台 总结 purpose model assignment"),
    entry("capabilities", "plugins", SettingsPage.PLUGIN_CENTER, "提示词插件 prompt plugins"),
    entry("capabilities", "web", SettingsPage.WEB_SERVICE, "Web 服务 地址 Token 令牌 token server address"),
    entry("data", "favorites", SettingsPage.EXTERNAL_FAVORITES, "外部 收藏 favorites external"),
    entry("data", "restore", SettingsPage.BACKUP_RESTORE, "恢复 还原 restore"),
    entry("data", "import", SettingsPage.DATA_IMPORT, "导入 迁移 import migration Flutter"),
)

internal fun searchSettings(query: String, translate: (String) -> String): List<SettingsEntry> {
    val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    if (terms.isEmpty()) return emptyList()
    return settingsSearchEntries.filter { item ->
        val text = "${translate(item.title)} ${translate(item.description)} ${item.keywords}"
        terms.all { text.contains(it, ignoreCase = true) }
    }
}

internal fun SettingsPage.parent(): SettingsPage = SettingsPage.MAIN

internal fun SettingsPage.groupPages(): List<SettingsPage> = when (this) {
    SettingsPage.AI_CONFIG, SettingsPage.AI_PURPOSE, SettingsPage.SPEECH ->
        listOf(SettingsPage.AI_CONFIG, SettingsPage.AI_PURPOSE, SettingsPage.SPEECH)
    SettingsPage.SKILLS, SettingsPage.PLUGIN_CENTER -> listOf(SettingsPage.SKILLS, SettingsPage.PLUGIN_CENTER)
    SettingsPage.MCP_SERVER, SettingsPage.WEB_SERVICE -> listOf(SettingsPage.MCP_SERVER, SettingsPage.WEB_SERVICE)
    SettingsPage.REMOTE_NEWS, SettingsPage.EXTERNAL_FAVORITES -> listOf(SettingsPage.REMOTE_NEWS, SettingsPage.EXTERNAL_FAVORITES)
    SettingsPage.BACKUP_SETTINGS, SettingsPage.BACKUP_RESTORE, SettingsPage.DATA_IMPORT ->
        listOf(SettingsPage.BACKUP_SETTINGS, SettingsPage.BACKUP_RESTORE, SettingsPage.DATA_IMPORT)
    else -> emptyList()
}

internal fun SettingsPage.tabKey(): String = "settings_design." + when (this) {
    SettingsPage.AI_CONFIG -> "ai"
    SettingsPage.AI_PURPOSE -> "ai_purpose"
    SettingsPage.SPEECH -> "speech"
    SettingsPage.SKILLS -> "skill_tab"
    SettingsPage.PLUGIN_CENTER -> "plugins"
    SettingsPage.MCP_SERVER -> "mcp"
    SettingsPage.WEB_SERVICE -> "web"
    SettingsPage.REMOTE_NEWS -> "news"
    SettingsPage.EXTERNAL_FAVORITES -> "favorites"
    SettingsPage.BACKUP_SETTINGS -> "backup"
    SettingsPage.BACKUP_RESTORE -> "restore"
    SettingsPage.DATA_IMPORT -> "import"
    else -> error("Page has no settings tab: $this")
}

internal fun SettingsPage.groupTitleKey(): String? = settingsHomeEntries
    .firstOrNull { it.destination.page == groupPages().firstOrNull() }?.title

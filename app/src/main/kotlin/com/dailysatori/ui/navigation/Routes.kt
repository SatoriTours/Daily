package com.dailysatori.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object ArticlesRoute
@Serializable data class ArticleDetailRoute(val articleId: Long)
@Serializable data object DiaryRoute
@Serializable data object BooksRoute
@Serializable data object BookSearchRoute
@Serializable data object AiChatRoute
@Serializable data object AiConfigRoute
@Serializable data class AiConfigEditRoute(val configId: Long? = null, val functionType: Int = 0)
@Serializable data object SettingsRoute
@Serializable data class ShareDialogRoute(val url: String)
@Serializable data object WeeklySummaryRoute
@Serializable data object BackupRestoreRoute
@Serializable data object BackupSettingsRoute
@Serializable data object PluginCenterRoute

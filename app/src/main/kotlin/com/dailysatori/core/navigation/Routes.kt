package com.dailysatori.core.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data class ArticleDetailRoute(val articleId: Long)
@Serializable data object BookSearchRoute
@Serializable data object AiConfigRoute
@Serializable data class AiConfigEditRoute(val configId: Long? = null, val functionType: Int = 0)
@Serializable data object SettingsRoute
@Serializable data class ShareDialogRoute(val url: String)

package com.dailysatori.core.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object ProfileRoute
@Serializable data object DataPrivacyRoute
@Serializable data object ProfileFavoritesRoute
@Serializable data object ProfileExternalFavoritesRoute
@Serializable data object TaskCenterRoute
@Serializable data class ArticleDetailRoute(val articleId: Long)
@Serializable data object BookSearchRoute
@Serializable data object BookContentSearchRoute
@Serializable data object AiConfigRoute
@Serializable data class AiConfigEditRoute(val configId: Long? = null)
@Serializable data object SettingsRoute
@Serializable data object ReminderListRoute
@Serializable data object ReminderSettingsRoute
@Serializable data class ReminderDetailRoute(val reminderId: String)
@Serializable data class ReminderEditRoute(val reminderId: String? = null)
@Serializable data class ReminderRoute(val reminderId: String)
@Serializable data class ShareDialogRoute(val url: String)

package com.dailysatori.core.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object ProfileRoute
@Serializable data object MyThoughtsRoute
@Serializable data object MyOpportunitiesRoute
@Serializable data class MyOpportunityRoute(val id: String)
@Serializable data class PersonalChatRoute(val kind: String = "", val key: String = "")
@Serializable data object DataPrivacyRoute
@Serializable data object ProfileFavoritesRoute
@Serializable data object ProfileExternalFavoritesRoute
@Serializable data object RemoteNewsSettingsRoute
@Serializable data class TaskCenterRoute(val recentFailures: Boolean = false)
@Serializable data class ArticleDetailRoute(val articleId: Long)
@Serializable data object BookSearchRoute
@Serializable data object BookContentSearchRoute
@Serializable data object AiConfigRoute
@Serializable data class AiConfigEditRoute(val configId: Long? = null)
@Serializable data object SettingsRoute
@Serializable data class ReminderListRoute(val todayOnly: Boolean = false)
@Serializable data object ReminderSettingsRoute
@Serializable data class ReminderDetailRoute(val reminderId: String)
@Serializable data class ReminderEditRoute(val reminderId: String? = null)
@Serializable data class ReminderRoute(val reminderId: String)
@Serializable data class ReminderAiBatchRoute(val batchId: String)
@Serializable data class ShareDialogRoute(val url: String)

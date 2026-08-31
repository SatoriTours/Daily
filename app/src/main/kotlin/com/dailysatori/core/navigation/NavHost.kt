package com.dailysatori.core.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.dailysatori.ui.feature.aiconfig.AiConfigEditScreen
import com.dailysatori.ui.feature.aiconfig.AiConfigScreen
import com.dailysatori.ui.feature.article.ArticleDetailScreen
import com.dailysatori.ui.feature.book.BookContentSearchScreen
import com.dailysatori.ui.feature.book.BookSearchScreen
import com.dailysatori.ui.feature.home.HomeScreen
import com.dailysatori.ui.feature.profile.DataPrivacyScreen
import com.dailysatori.ui.feature.profile.ProfileScreen
import com.dailysatori.ui.feature.article.ArticleListScreen
import com.dailysatori.ui.feature.settings.taskcenter.TaskCenterScreen
import com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesSettingsScreen
import com.dailysatori.ui.feature.settings.SettingsScreen
import com.dailysatori.ui.feature.settings.SettingsViewModel
import com.dailysatori.ui.feature.share.ShareDialogScreen
import com.dailysatori.ui.feature.reminder.ReminderDetailScreen
import com.dailysatori.ui.feature.reminder.ReminderEditScreen
import com.dailysatori.ui.feature.reminder.ReminderListScreen

private const val ANIM_DURATION = 350
private const val SELECTED_BOOK_ID_KEY = "selectedBookId"
private const val SELECTED_VIEWPOINT_ID_KEY = "selectedViewpointId"
private const val BOOK_ANALYSIS_MESSAGE_KEY = "bookAnalysisMessage"

@Composable
fun DailySatoriNavHost(navController: NavHostController, settingsViewModel: SettingsViewModel) {
    NavHost(navController, startDestination = HomeRoute) {
        composable<HomeRoute>(
            enterTransition = { fadeIn(animationSpec = tween(ANIM_DURATION)) },
            exitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(ANIM_DURATION),
                    targetOffsetX = { -it },
                ) + fadeOut(animationSpec = tween(ANIM_DURATION))
            },
        ) { backStackEntry ->
            val selectedBookId by backStackEntry.savedStateHandle
                .getStateFlow<Long?>(SELECTED_BOOK_ID_KEY, null)
                .collectAsState()
            val bookAnalysisMessage by backStackEntry.savedStateHandle
                .getStateFlow<String?>(BOOK_ANALYSIS_MESSAGE_KEY, null)
                .collectAsState()
            val selectedViewpointId by backStackEntry.savedStateHandle
                .getStateFlow<Long?>(SELECTED_VIEWPOINT_ID_KEY, null)
                .collectAsState()

            HomeScreen(
                selectedBookId = selectedBookId,
                selectedViewpointId = selectedViewpointId,
                bookAnalysisMessage = bookAnalysisMessage,
                onSelectedBookConsumed = {
                    backStackEntry.savedStateHandle[SELECTED_BOOK_ID_KEY] = null
                    backStackEntry.savedStateHandle[SELECTED_VIEWPOINT_ID_KEY] = null
                },
                onBookAnalysisMessageConsumed = {
                    backStackEntry.savedStateHandle[BOOK_ANALYSIS_MESSAGE_KEY] = null
                },
                onArticleClick = { id -> navController.navigate(ArticleDetailRoute(id)) },
                onAiArticleClick = { id -> navController.navigate(ArticleDetailRoute(id)) },
                onProfileClick = { navController.navigate(ProfileRoute) },
                settingsViewModel = settingsViewModel,
            )
        }

        composable<ProfileRoute> {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onReminders = { navController.navigate(ReminderListRoute) },
                onAddReminder = { navController.navigate(ReminderEditRoute()) },
                onFavorites = { navController.navigate(ProfileFavoritesRoute) },
                onExternalFavorites = { navController.navigate(ProfileExternalFavoritesRoute) },
                onTasks = { navController.navigate(TaskCenterRoute) },
                onSettings = { navController.navigate(SettingsRoute) },
                onPrivacy = { navController.navigate(DataPrivacyRoute) },
            )
        }
        composable<DataPrivacyRoute> { DataPrivacyScreen(onBack = { navController.popBackStack() }) }
        composable<ProfileFavoritesRoute> {
            ArticleListScreen(
                onArticleClick = { id -> navController.navigate(ArticleDetailRoute(id)) },
                onBack = { navController.popBackStack() },
                showFavoritesOnly = true,
                lockFavoritesFilter = true,
            )
        }
        composable<ProfileExternalFavoritesRoute> { ExternalFavoritesSettingsScreen(onBack = { navController.popBackStack() }) }
        composable<TaskCenterRoute> { TaskCenterScreen(onBack = { navController.popBackStack() }) }

        composable<ArticleDetailRoute>(
            enterTransition = {
                slideInHorizontally(
                    animationSpec = tween(ANIM_DURATION),
                    initialOffsetX = { it },
                ) + fadeIn(animationSpec = tween(ANIM_DURATION))
            },
            exitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(ANIM_DURATION),
                    targetOffsetX = { it },
                ) + fadeOut(animationSpec = tween(ANIM_DURATION))
            },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<ArticleDetailRoute>()
            ArticleDetailScreen(
                articleId = route.articleId,
                onBack = { navController.popBackStack() },
            )
        }

        composable<BookSearchRoute>(
            enterTransition = {
                slideInHorizontally(
                    animationSpec = tween(ANIM_DURATION),
                    initialOffsetX = { it },
                ) + fadeIn(animationSpec = tween(ANIM_DURATION))
            },
            exitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(ANIM_DURATION),
                    targetOffsetX = { it },
                ) + fadeOut(animationSpec = tween(ANIM_DURATION))
            },
        ) {
            BookSearchScreen(
                onBack = { if (shouldNavigateHomeAfterPop(navController.popBackStack())) navController.navigate(HomeRoute) },
                onBookAdded = { bookId, message ->
                    val targetEntry = navController.previousBackStackEntry
                    targetEntry?.savedStateHandle?.set(SELECTED_BOOK_ID_KEY, bookId)
                    targetEntry?.savedStateHandle?.set(BOOK_ANALYSIS_MESSAGE_KEY, message)
                    if (shouldNavigateHomeAfterPop(navController.popBackStack())) {
                        navController.navigate(HomeRoute)
                        navController.currentBackStackEntry?.savedStateHandle?.set(SELECTED_BOOK_ID_KEY, bookId)
                        navController.currentBackStackEntry?.savedStateHandle?.set(BOOK_ANALYSIS_MESSAGE_KEY, message)
                    }
                },
            )
        }

        composable<BookContentSearchRoute>(
            enterTransition = {
                slideInHorizontally(
                    animationSpec = tween(ANIM_DURATION),
                    initialOffsetX = { it },
                ) + fadeIn(animationSpec = tween(ANIM_DURATION))
            },
            exitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(ANIM_DURATION),
                    targetOffsetX = { it },
                ) + fadeOut(animationSpec = tween(ANIM_DURATION))
            },
        ) {
            BookContentSearchScreen(
                onBack = { if (shouldNavigateHomeAfterPop(navController.popBackStack())) navController.navigate(HomeRoute) },
                onResultClick = { bookId, viewpointId ->
                    val targetEntry = navController.previousBackStackEntry
                    targetEntry?.savedStateHandle?.set(SELECTED_BOOK_ID_KEY, bookId)
                    targetEntry?.savedStateHandle?.set(SELECTED_VIEWPOINT_ID_KEY, viewpointId)
                    if (shouldNavigateHomeAfterPop(navController.popBackStack())) {
                        navController.navigate(HomeRoute)
                        navController.currentBackStackEntry?.savedStateHandle?.set(SELECTED_BOOK_ID_KEY, bookId)
                        navController.currentBackStackEntry?.savedStateHandle?.set(SELECTED_VIEWPOINT_ID_KEY, viewpointId)
                    }
                },
            )
        }

        composable<AiConfigRoute>(
            enterTransition = {
                slideInHorizontally(
                    animationSpec = tween(ANIM_DURATION),
                    initialOffsetX = { it },
                ) + fadeIn(animationSpec = tween(ANIM_DURATION))
            },
            exitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(ANIM_DURATION),
                    targetOffsetX = { it },
                ) + fadeOut(animationSpec = tween(ANIM_DURATION))
            },
        ) {
            AiConfigScreen(
                onBack = { navController.popBackStack() },
                onEditConfig = { id -> navController.navigate(AiConfigEditRoute(configId = id)) },
            )
        }

        composable<AiConfigEditRoute>(
            enterTransition = {
                slideInHorizontally(
                    animationSpec = tween(ANIM_DURATION),
                    initialOffsetX = { it },
                ) + fadeIn(animationSpec = tween(ANIM_DURATION))
            },
            exitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(ANIM_DURATION),
                    targetOffsetX = { it },
                ) + fadeOut(animationSpec = tween(ANIM_DURATION))
            },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<AiConfigEditRoute>()
            AiConfigEditScreen(
                configId = route.configId,
                onBack = { navController.popBackStack() },
            )
        }

        composable<SettingsRoute>(
            enterTransition = {
                slideInHorizontally(
                    animationSpec = tween(ANIM_DURATION),
                    initialOffsetX = { it },
                ) + fadeIn(animationSpec = tween(ANIM_DURATION))
            },
            exitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(ANIM_DURATION),
                    targetOffsetX = { it },
                ) + fadeOut(animationSpec = tween(ANIM_DURATION))
            },
        ) {
            SettingsScreen(
                viewModel = settingsViewModel,
            )
        }

        composable<ReminderListRoute> {
            ReminderListScreen(
                onBack = { navController.popBackStack() },
                onAddReminder = { navController.navigate(ReminderEditRoute()) },
                onOpenReminder = { id -> navController.navigate(ReminderDetailRoute(id)) },
                onOpenSettings = { navController.navigate(ReminderSettingsRoute) },
            )
        }

        composable<ReminderSettingsRoute> {
            com.dailysatori.ui.feature.settings.reminder.ReminderSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable<ReminderDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ReminderDetailRoute>()
            ReminderDetailScreen(
                reminderId = route.reminderId,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(ReminderEditRoute(id)) },
            )
        }

        composable<ReminderEditRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ReminderEditRoute>()
            ReminderEditScreen(
                reminderId = route.reminderId,
                onBack = { navController.popBackStack() },
                onSaved = { id -> navController.navigate(ReminderDetailRoute(id)) { popUpTo<ReminderEditRoute> { inclusive = true } } },
            )
        }

        composable<ReminderRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ReminderRoute>()
            ReminderDetailScreen(
                reminderId = route.reminderId,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(ReminderEditRoute(id)) },
            )
        }

        composable<ShareDialogRoute>(
            enterTransition = {
                slideInHorizontally(
                    animationSpec = tween(ANIM_DURATION),
                    initialOffsetX = { it },
                ) + fadeIn(animationSpec = tween(ANIM_DURATION))
            },
            exitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(ANIM_DURATION),
                    targetOffsetX = { it },
                ) + fadeOut(animationSpec = tween(ANIM_DURATION))
            },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<ShareDialogRoute>()
            ShareDialogScreen(
                url = route.url,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

fun shouldNavigateHomeAfterPop(popBackStackSucceeded: Boolean): Boolean = !popBackStackSucceeded

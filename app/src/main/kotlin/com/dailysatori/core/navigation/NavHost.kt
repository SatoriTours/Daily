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
import com.dailysatori.ui.feature.settings.remotenews.RemoteNewsSettingsScreen
import com.dailysatori.ui.feature.settings.SettingsScreen
import com.dailysatori.ui.feature.settings.SettingsViewModel
import com.dailysatori.ui.feature.share.ShareDialogScreen
import com.dailysatori.ui.feature.reminder.ReminderDetailScreen
import com.dailysatori.ui.feature.reminder.ReminderEditScreen
import com.dailysatori.ui.feature.reminder.ReminderListScreen
import com.dailysatori.ui.feature.myspace.*
import com.dailysatori.ui.feature.diary.DiaryThoughtScreen
import com.dailysatori.ui.feature.diary.DiaryThoughtViewModel
import org.koin.androidx.compose.koinViewModel

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
                onThoughts = { navController.navigate(MyThoughtsRoute) },
                onReminders = { navController.navigate(ReminderListRoute()) },
                onTodayReminders = { navController.navigate(ReminderListRoute(todayOnly = true)) },
                onReminder = { navController.navigate(ReminderDetailRoute(it)) },
                onAddReminder = { navController.navigate(ReminderEditRoute()) },
                onOpportunities = { navController.navigate(MyOpportunitiesRoute) },
                onOpportunity = { navController.navigate(MyOpportunityRoute(it)) },
                onChat = { navController.navigate(PersonalChatRoute()) },
                settingsViewModel = settingsViewModel,
            )
        }

        composable<MyThoughtsRoute> {
            val viewModel: DiaryThoughtViewModel = koinViewModel()
            DiaryThoughtScreen(viewModel, onBack = { navController.popBackStack() }, onDiaryClick = {
                com.dailysatori.core.recording.DiaryRecordingOpenRequest.open(it)
                navController.popBackStack(HomeRoute, inclusive = false)
            }, onDiscuss = { navController.navigate(PersonalChatRoute("thought", thoughtChatKey(it))) })
        }
        composable<MyOpportunitiesRoute> {
            NewsOpportunityListScreen(onBack = { navController.popBackStack() },
                onThoughts = { navController.navigate(MyThoughtsRoute) },
                onOpen = { navController.navigate(MyOpportunityRoute(it)) },
                onArticle = { navController.navigate(ArticleDetailRoute(it)) })
        }
        composable<MyOpportunityRoute> { entry ->
            val route = entry.toRoute<MyOpportunityRoute>()
            NewsOpportunityDetailScreen(route.id, onBack = { navController.popBackStack() },
                onChat = { navController.navigate(PersonalChatRoute("opportunity", route.id)) },
                onArticle = { navController.navigate(ArticleDetailRoute(it)) })
        }
        composable<PersonalChatRoute> { entry ->
            val route = entry.toRoute<PersonalChatRoute>()
            PersonalChatScreen(route.kind, route.key, onBack = { navController.popBackStack() }, onArticle = { navController.navigate(ArticleDetailRoute(it)) })
        }
        composable<ProfileRoute> {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onFavorites = { navController.navigate(ProfileFavoritesRoute) },
                onExternalFavorites = { navController.navigate(ProfileExternalFavoritesRoute) },
                onRemoteNews = { navController.navigate(RemoteNewsSettingsRoute) },
                onTasks = { navController.navigate(TaskCenterRoute()) },
                onFailedTasks = { navController.navigate(TaskCenterRoute(recentFailures = true)) },
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
        composable<RemoteNewsSettingsRoute> { RemoteNewsSettingsScreen(onBack = { navController.popBackStack() }) }
        composable<TaskCenterRoute> { entry ->
            TaskCenterScreen(onBack = { navController.popBackStack() }, recentFailures = entry.toRoute<TaskCenterRoute>().recentFailures)
        }

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
                onBack = { navController.popBackStack() },
            )
        }

        composable<ReminderListRoute> { entry ->
            ReminderListScreen(
                initialTodayOnly = entry.toRoute<ReminderListRoute>().todayOnly,
                onBack = { navController.popBackStack() },
                onAddReminder = { navController.navigate(ReminderEditRoute()) },
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
                onBatchSubmitted = { batchId -> navController.navigate(ReminderAiBatchRoute(batchId)) },
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

        composable<ReminderAiBatchRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ReminderAiBatchRoute>()
            com.dailysatori.ui.feature.reminder.ReminderAiBatchScreen(
                batchId = route.batchId,
                onBack = { navController.popBackStack() },
                onOpenSuccessor = { batchId -> navController.navigate(ReminderAiBatchRoute(batchId)) },
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

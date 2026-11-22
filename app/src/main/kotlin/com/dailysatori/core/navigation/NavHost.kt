package com.dailysatori.core.navigation

import com.dailysatori.core.diagnostics.LocalUiVisit
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import com.dailysatori.ui.feature.aichat.AssistantOpenRequest
import com.dailysatori.ui.feature.aichat.GlobalAssistantHost
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.dailysatori.core.diagnostics.measuredDestination
import com.dailysatori.core.diagnostics.rememberNavigationPerformance
import androidx.navigation.toRoute
import com.dailysatori.ui.feature.aiconfig.AiConfigEditScreen
import com.dailysatori.ui.feature.aiconfig.AiConfigScreen
import com.dailysatori.ui.feature.article.ArticleDetailScreen
import com.dailysatori.ui.feature.book.BookContentSearchScreen
import com.dailysatori.ui.feature.book.BookSearchScreen
import com.dailysatori.ui.feature.home.HomeScreen
import com.dailysatori.ui.feature.profile.DataPrivacyScreen
import com.dailysatori.ui.feature.article.ArticleListScreen
import com.dailysatori.ui.feature.settings.taskcenter.TaskCenterScreen
import com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesSettingsScreen
import com.dailysatori.ui.feature.settings.remotenews.RemoteNewsSettingsScreen
import com.dailysatori.ui.feature.settings.speech.SpeechSettingsScreen
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
    val phoneNavigation = remember(navController) {
        PhoneNavigation(
            openHistory = { navController.navigate(PhoneHistoryRoute(it)) },
            openLedger = { navController.navigate(LedgerEntryRoute(it)) },
            openTodo = { navController.navigate(ReminderDetailRoute(it)) },
        )
    }
    CompositionLocalProvider(LocalPhoneNavigation provides phoneNavigation) {
        GlobalAssistantHost(navController) { openAssistant ->
            DailySatoriNavigationGraph(navController, settingsViewModel, openAssistant)
        }
    }
}

@Composable
private fun DailySatoriNavigationGraph(navController: NavHostController, settingsViewModel: SettingsViewModel,
    openAssistant: (AssistantOpenRequest) -> Unit) {
    val performance = rememberNavigationPerformance(navController)
    NavHost(navController, startDestination = HomeRoute) {
        measuredDestination<LifeArchiveRoute>(performance) {
            com.dailysatori.ui.feature.lifearchive.LifeArchiveScreen(
                onBack = { navController.popBackStack() },
                onConfigureAi = { navController.navigate(AiConfigRoute) },
                onReminder = { navController.navigate(ReminderDetailRoute(it)) },
            )
        }
        measuredDestination<HomeRoute>(
            performance,
            enterTransition = { EnterTransition.None },
            popEnterTransition = { fadeIn(animationSpec = tween(ANIM_DURATION)) },
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
                onProfileClick = { navController.navigate(SettingsRoute) },
                onFavorites = { navController.navigate(ProfileFavoritesRoute) },
                onTasks = { navController.navigate(TaskCenterRoute()) },
                onThoughts = { navController.navigate(MyThoughtsRoute()) },
                onThought = { navController.navigate(MyThoughtsRoute(thoughtChatKey(it))) },
                onReminders = { navController.navigate(ReminderListRoute()) },
                onLifeArchive = { navController.navigate(LifeArchiveRoute) },
                onReminder = { navController.navigate(ReminderDetailRoute(it)) },
                onAddReminder = { navController.navigate(ReminderEditRoute()) },
                onOpportunities = { navController.navigate(MyOpportunitiesRoute) },
                onBriefing = { navController.navigate(NewsBriefingRoute(it)) },
                onOpportunity = { navController.navigate(MyOpportunityRoute(it)) },
                onChat = { openAssistant(AssistantOpenRequest()) },
                onIdeaTopics = { navController.navigate(IdeaTopicListRoute) },
                onIdeaTopic = { navController.navigate(IdeaTopicDetailRoute(it)) },
                onLedger = { navController.navigate(LedgerRoute) },
                settingsViewModel = settingsViewModel,
            )
        }

        measuredDestination<NewsBriefingRoute>(performance) { entry ->
            com.dailysatori.ui.feature.unifiednews.UnifiedNewsBriefingScreen(
                summaryId = entry.toRoute<NewsBriefingRoute>().summaryId,
                onBack = { navController.popBackStack() },
                onArticle = { navController.navigate(ArticleDetailRoute(it)) },
                onOpportunity = { navController.navigate(MyOpportunityRoute(it)) },
            )
        }
        measuredDestination<MyThoughtsRoute>(performance) { entry ->
            val viewModel: DiaryThoughtViewModel = koinViewModel()
            DiaryThoughtScreen(viewModel, onBack = { navController.popBackStack() }, onDiaryClick = {
                com.dailysatori.core.recording.DiaryRecordingOpenRequest.open(it)
                navController.popBackStack(HomeRoute, inclusive = false)
            }, onDiscuss = { openAssistant(AssistantOpenRequest("thought", thoughtChatKey(it))) },
                focusThoughtKey = entry.toRoute<MyThoughtsRoute>().thoughtKey)
        }
        measuredDestination<MyOpportunitiesRoute>(performance) {
            NewsOpportunityListScreen(onBack = { navController.popBackStack() },
                onThoughts = { navController.navigate(MyThoughtsRoute()) },
                onOpen = { navController.navigate(MyOpportunityRoute(it)) },
                onArticle = { navController.navigate(ArticleDetailRoute(it)) },
                onTopic = { navController.navigate(IdeaTopicDetailRoute(it)) })
        }
        measuredDestination<MyOpportunityRoute>(performance) { entry ->
            val route = entry.toRoute<MyOpportunityRoute>()
            NewsOpportunityDetailScreen(route.id, onBack = { navController.popBackStack() },
                onChat = { openAssistant(AssistantOpenRequest("opportunity", route.id)) },
                onArticle = { navController.navigate(ArticleDetailRoute(it)) },
                onTopic = { navController.navigate(IdeaTopicDetailRoute(it)) })
        }
        measuredDestination<PersonalChatRoute>(performance) { entry ->
            val route = entry.toRoute<PersonalChatRoute>()
            val visit = LocalUiVisit.current
            LaunchedEffect(route, visit) {
                if (visit == null) return@LaunchedEffect
                openAssistant(AssistantOpenRequest(route.kind, route.key).also { it.performanceVisit = visit })
                navController.popBackStack()
            }
        }
        measuredDestination<AssistantRoute>(performance) { entry ->
            val route = entry.toRoute<AssistantRoute>()
            val visit = LocalUiVisit.current
            LaunchedEffect(route, visit) {
                if (visit == null) return@LaunchedEffect
                openAssistant(AssistantOpenRequest(sessionId = route.sessionId).also { it.performanceVisit = visit })
                navController.popBackStack()
            }
        }
        measuredDestination<ProfileRoute>(performance) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        measuredDestination<LedgerRoute>(performance) { com.dailysatori.ui.feature.ledger.LedgerScreen(onBack = { navController.popBackStack() }) }
        measuredDestination<LedgerEntryRoute>(performance) { entry ->
            com.dailysatori.ui.feature.ledger.LedgerEntryScreen(entry.toRoute<LedgerEntryRoute>().entryId,
                onBack = { navController.popBackStack() })
        }
        measuredDestination<PhoneHistoryRoute>(performance) { entry ->
            com.dailysatori.ui.feature.phone.PhoneHistoryScreen(onBack = { navController.popBackStack() },
                messageId = entry.toRoute<PhoneHistoryRoute>().messageId)
        }
        measuredDestination<DataPrivacyRoute>(performance) { DataPrivacyScreen(onBack = { navController.popBackStack() }) }
        measuredDestination<ProfileFavoritesRoute>(performance) {
            ArticleListScreen(
                onArticleClick = { id -> navController.navigate(ArticleDetailRoute(id)) },
                onBack = { navController.popBackStack() },
                showFavoritesOnly = true,
                lockFavoritesFilter = true,
            )
        }
        measuredDestination<ProfileExternalFavoritesRoute>(performance) { ExternalFavoritesSettingsScreen(onBack = { navController.popBackStack() }) }
        measuredDestination<RemoteNewsSettingsRoute>(performance) { RemoteNewsSettingsScreen(onBack = { navController.popBackStack() }) }
        measuredDestination<SpeechSettingsRoute>(performance) { SpeechSettingsScreen(onBack = { navController.popBackStack() }) }
        measuredDestination<TaskCenterRoute>(performance) { entry ->
            TaskCenterScreen(onBack = { navController.popBackStack() }, recentFailures = entry.toRoute<TaskCenterRoute>().recentFailures)
        }

        measuredDestination<ArticleDetailRoute>(
            performance,
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

        measuredDestination<BookSearchRoute>(
            performance,
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

        measuredDestination<BookContentSearchRoute>(
            performance,
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

        measuredDestination<AiConfigRoute>(
            performance,
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

        measuredDestination<AiConfigEditRoute>(
            performance,
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

        measuredDestination<SettingsRoute>(
            performance,
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

        measuredDestination<ReminderListRoute>(performance) { entry ->
            ReminderListScreen(
                initialTodayOnly = entry.toRoute<ReminderListRoute>().todayOnly,
                onBack = { navController.popBackStack() },
                onAddReminder = { navController.navigate(ReminderEditRoute()) },
                onOpenSettings = { navController.navigate(ReminderSettingsRoute) },
            )
        }

        measuredDestination<ReminderSettingsRoute>(performance) {
            com.dailysatori.ui.feature.settings.reminder.ReminderSettingsScreen(onBack = { navController.popBackStack() })
        }
        measuredDestination<SmsSettingsRoute>(performance) {
            com.dailysatori.ui.feature.settings.sms.SmsSettingsScreen(onBack = { navController.popBackStack() })
        }

        measuredDestination<ReminderDetailRoute>(performance) { backStackEntry ->
            val route = backStackEntry.toRoute<ReminderDetailRoute>()
            ReminderDetailScreen(
                reminderId = route.reminderId,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(ReminderEditRoute(id)) },
            )
        }

        measuredDestination<ReminderEditRoute>(performance) { backStackEntry ->
            val route = backStackEntry.toRoute<ReminderEditRoute>()
            ReminderEditScreen(
                reminderId = route.reminderId,
                onBack = { navController.popBackStack() },
                onSaved = { id -> navController.navigate(ReminderDetailRoute(id)) { popUpTo<ReminderEditRoute> { inclusive = true } } },
                onBatchSubmitted = { batchId -> navController.navigate(ReminderAiBatchRoute(batchId)) },
            )
        }

        measuredDestination<ReminderRoute>(performance) { backStackEntry ->
            val route = backStackEntry.toRoute<ReminderRoute>()
            ReminderDetailScreen(
                reminderId = route.reminderId,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(ReminderEditRoute(id)) },
            )
        }

        measuredDestination<ReminderAiBatchRoute>(performance) { backStackEntry ->
            val route = backStackEntry.toRoute<ReminderAiBatchRoute>()
            com.dailysatori.ui.feature.reminder.ReminderAiBatchScreen(
                batchId = route.batchId,
                onBack = { navController.popBackStack() },
                onOpenSuccessor = { batchId -> navController.navigate(ReminderAiBatchRoute(batchId)) },
            )
        }

        measuredDestination<ShareDialogRoute>(
            performance,
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

        measuredDestination<IdeaTopicListRoute>(performance) {
            com.dailysatori.ui.feature.ideatopic.IdeaTopicListScreen(
                onBack = { navController.popBackStack() },
                onOpen = { topicId -> navController.navigate(IdeaTopicDetailRoute(topicId)) },
            )
        }
        measuredDestination<IdeaTopicDetailRoute>(performance) { entry ->
            val route = entry.toRoute<IdeaTopicDetailRoute>()
            com.dailysatori.ui.feature.ideatopic.IdeaTopicDetailScreen(
                id = route.id,
                onBack = { navController.popBackStack() },
                onSession = { topicId, sessionId ->
                    navController.navigate(IdeaTopicSessionRoute(topicId, sessionId))
                },
                onSource = { snapshot ->
                    snapshot.originalRecordId?.toLongOrNull()?.let { recordId ->
                        if (snapshot.key.type == com.dailysatori.service.ideatopic.IdeaSourceTypes.Diary) {
                            com.dailysatori.core.recording.DiaryRecordingOpenRequest.open(recordId)
                            navController.popBackStack(HomeRoute, inclusive = false)
                        } else navController.navigate(ArticleDetailRoute(recordId))
                    }
                },
            )
        }
        measuredDestination<IdeaTopicSessionRoute>(performance) { entry ->
            val route = entry.toRoute<IdeaTopicSessionRoute>()
            com.dailysatori.ui.feature.ideatopic.IdeaTopicSessionScreen(
                topicId = route.topicId,
                sessionId = route.sessionId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

fun shouldNavigateHomeAfterPop(popBackStackSucceeded: Boolean): Boolean = !popBackStackSucceeded

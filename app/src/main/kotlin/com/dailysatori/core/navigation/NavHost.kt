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
import com.dailysatori.ui.feature.book.BookSearchScreen
import com.dailysatori.ui.feature.home.HomeScreen
import com.dailysatori.ui.feature.settings.SettingsScreen
import com.dailysatori.ui.feature.share.ShareDialogScreen

private const val ANIM_DURATION = 350
private const val SELECTED_BOOK_ID_KEY = "selectedBookId"
private const val BOOK_ANALYSIS_MESSAGE_KEY = "bookAnalysisMessage"

@Composable
fun DailySatoriNavHost(navController: NavHostController) {
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

            HomeScreen(
                selectedBookId = selectedBookId,
                bookAnalysisMessage = bookAnalysisMessage,
                onSelectedBookConsumed = { backStackEntry.savedStateHandle[SELECTED_BOOK_ID_KEY] = null },
                onArticleClick = { id -> navController.navigate(ArticleDetailRoute(id)) },
                onBookSearchClick = {
                    backStackEntry.savedStateHandle[BOOK_ANALYSIS_MESSAGE_KEY] = null
                    navController.navigate(BookSearchRoute)
                },
                onAiArticleClick = { id -> navController.navigate(ArticleDetailRoute(id)) },
            )
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
                onBack = { navController.popBackStack() },
                onBookAdded = { bookId, message ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(SELECTED_BOOK_ID_KEY, bookId)
                    navController.previousBackStackEntry?.savedStateHandle?.set(BOOK_ANALYSIS_MESSAGE_KEY, message)
                    navController.popBackStack()
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
            SettingsScreen()
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

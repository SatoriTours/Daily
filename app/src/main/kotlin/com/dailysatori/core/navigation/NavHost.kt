package com.dailysatori.core.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
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
        ) {
            HomeScreen(
                onArticleClick = { id -> navController.navigate(ArticleDetailRoute(id)) },
                onBookSearchClick = { navController.navigate(BookSearchRoute) },
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

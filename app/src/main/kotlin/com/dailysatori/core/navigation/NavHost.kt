package com.dailysatori.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.dailysatori.ui.feature.aichat.AiChatScreen
import com.dailysatori.ui.feature.aiconfig.AiConfigEditScreen
import com.dailysatori.ui.feature.aiconfig.AiConfigScreen
import com.dailysatori.ui.feature.article.ArticleDetailScreen
import com.dailysatori.ui.feature.article.ArticleListScreen
import com.dailysatori.ui.feature.book.BookSearchScreen
import com.dailysatori.ui.feature.book.BooksScreen
import com.dailysatori.ui.feature.diary.DiaryScreen
import com.dailysatori.ui.feature.home.HomeScreen
import com.dailysatori.ui.feature.settings.BackupRestoreScreen
import com.dailysatori.ui.feature.settings.BackupSettingsScreen
import com.dailysatori.ui.feature.settings.DataImportScreen
import com.dailysatori.ui.feature.settings.PluginCenterScreen
import com.dailysatori.ui.feature.settings.SettingsScreen
import com.dailysatori.ui.feature.share.ShareDialogScreen
import com.dailysatori.ui.feature.settings.WeeklySummaryScreen

@Composable
fun DailySatoriNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onArticleClick = { id -> navController.navigate(ArticleDetailRoute(id)) },
            )
        }

        composable<ArticlesRoute> {
            ArticleListScreen(
                onArticleClick = { id -> navController.navigate(ArticleDetailRoute(id)) },
            )
        }

        composable<ArticleDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ArticleDetailRoute>()
            ArticleDetailScreen(
                articleId = route.articleId,
                onBack = { navController.popBackStack() },
            )
        }

        composable<DiaryRoute> { DiaryScreen() }

        composable<BooksRoute> {
            BooksScreen(
                onSearchClick = { navController.navigate(BookSearchRoute) },
            )
        }

        composable<BookSearchRoute> {
            BookSearchScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable<AiChatRoute> { AiChatScreen() }

        composable<AiConfigRoute> {
            AiConfigScreen(
                onBack = { navController.popBackStack() },
                onEditConfig = { id -> navController.navigate(AiConfigEditRoute(configId = id)) },
            )
        }

        composable<AiConfigEditRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AiConfigEditRoute>()
            AiConfigEditScreen(
                configId = route.configId,
                functionType = route.functionType,
                onBack = { navController.popBackStack() },
            )
        }

        composable<SettingsRoute> {
            SettingsScreen()
        }

        composable<ShareDialogRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ShareDialogRoute>()
            ShareDialogScreen(
                url = route.url,
                onBack = { navController.popBackStack() },
            )
        }

        composable<WeeklySummaryRoute> { WeeklySummaryScreen() }

        composable<BackupRestoreRoute> {
            BackupRestoreScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable<BackupSettingsRoute> {
            BackupSettingsScreen(
                onBack = { navController.popBackStack() },
                onRestore = { navController.navigate(BackupRestoreRoute) },
            )
        }

        composable<PluginCenterRoute> {
            PluginCenterScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable<DataImportRoute> {
            DataImportScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

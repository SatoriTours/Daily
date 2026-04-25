package com.dailysatori.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.dailysatori.ui.pages.PlaceholderScreen
import com.dailysatori.ui.pages.home.HomeScreen

@Composable
fun DailySatoriNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = HomeRoute) {
        composable<HomeRoute> { HomeScreen() }
        composable<ArticlesRoute> { PlaceholderScreen("Articles") }
        composable<ArticleDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ArticleDetailRoute>()
            PlaceholderScreen("Article Detail #${route.articleId}")
        }
        composable<DiaryRoute> { PlaceholderScreen("Diary") }
        composable<BooksRoute> { PlaceholderScreen("Books") }
        composable<BookSearchRoute> { PlaceholderScreen("Book Search") }
        composable<AiChatRoute> { PlaceholderScreen("AI Chat") }
        composable<AiConfigRoute> { PlaceholderScreen("AI Config") }
        composable<AiConfigEditRoute> { PlaceholderScreen("AI Config Edit") }
        composable<SettingsRoute> { PlaceholderScreen("Settings") }
        composable<ShareDialogRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ShareDialogRoute>()
            PlaceholderScreen("Share: ${route.url}")
        }
        composable<WeeklySummaryRoute> { PlaceholderScreen("Weekly Summary") }
        composable<BackupRestoreRoute> { PlaceholderScreen("Backup Restore") }
        composable<BackupSettingsRoute> { PlaceholderScreen("Backup Settings") }
        composable<PluginCenterRoute> { PlaceholderScreen("Plugin Center") }
    }
}

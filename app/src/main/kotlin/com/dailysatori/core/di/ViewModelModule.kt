package com.dailysatori.core.di

import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.data.repository.WeeklySummaryRepository
import com.dailysatori.platform.FileManager
import com.dailysatori.core.service.AppUpgradeService
import com.dailysatori.core.service.WebServerService
import com.dailysatori.service.backup.BackupService
import com.dailysatori.service.mcp.McpAgentService
import com.dailysatori.service.plugin.PluginService
import com.dailysatori.service.setting.SettingService
import com.dailysatori.service.weekly.WeeklySummaryService
import com.dailysatori.ui.feature.aichat.AiChatViewModel
import com.dailysatori.ui.feature.aiconfig.AiConfigViewModel
import com.dailysatori.ui.feature.article.ArticleDetailViewModel
import com.dailysatori.ui.feature.article.ArticlesViewModel
import com.dailysatori.ui.feature.settings.BackupRestoreViewModel
import com.dailysatori.ui.feature.settings.BackupSettingsViewModel
import com.dailysatori.ui.feature.book.BooksViewModel
import com.dailysatori.ui.feature.diary.DiaryViewModel
import com.dailysatori.ui.feature.settings.PluginCenterViewModel
import com.dailysatori.ui.feature.settings.SettingsViewModel
import com.dailysatori.ui.feature.share.ShareDialogViewModel
import com.dailysatori.ui.feature.settings.WeeklySummaryViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

val viewModelModule: Module = module {
    viewModel {
        ArticlesViewModel(
            articleRepo = get<ArticleRepository>(),
            tagRepo = get<TagRepository>(),
        )
    }
    viewModel { params ->
        ArticleDetailViewModel(
            articleId = params.get<Long>(),
            articleRepo = get<ArticleRepository>(),
            tagRepo = get<TagRepository>(),
        )
    }
    viewModel {
        DiaryViewModel(
            diaryRepo = get<DiaryRepository>(),
        )
    }
    viewModel {
        BooksViewModel(
            bookRepo = get<BookRepository>(),
            viewpointRepo = get<BookViewpointRepository>(),
        )
    }
    viewModel {
        AiChatViewModel(
            mcpAgentService = get<McpAgentService>(),
        )
    }
    viewModel {
        AiConfigViewModel(
            repo = get<AIConfigRepository>(),
        )
    }
    viewModel {
        ShareDialogViewModel(
            articleRepo = get<ArticleRepository>(),
        )
    }
    viewModel {
        WeeklySummaryViewModel(
            weeklySummaryService = get<WeeklySummaryService>(),
            repo = get<WeeklySummaryRepository>(),
        )
    }
    viewModel {
        BackupSettingsViewModel(
            settingService = get<SettingService>(),
            backupService = get<BackupService>(),
            fileManager = get<FileManager>(),
        )
    }
    viewModel {
        BackupRestoreViewModel(
            settingService = get<SettingService>(),
            fileManager = get<FileManager>(),
        )
    }
    viewModel {
        PluginCenterViewModel(
            pluginService = get<PluginService>(),
            settingRepo = get<SettingRepository>(),
        )
    }
    viewModel {
        SettingsViewModel(
            settingService = get<SettingService>(),
            webServerService = get<WebServerService>(),
            appUpgradeService = get<AppUpgradeService>(),
        )
    }
}

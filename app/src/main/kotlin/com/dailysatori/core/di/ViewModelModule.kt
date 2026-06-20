package com.dailysatori.core.di

import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.AppUrlIntakeViewModel
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointAiRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.ChatConversationRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.data.repository.WeeklySummaryRepository
import com.dailysatori.platform.FileManager
import com.dailysatori.core.service.AppUpgradeService
import com.dailysatori.core.service.WebServerService
import com.dailysatori.service.backup.BackupService
import com.dailysatori.service.book.BookReflectionService
import com.dailysatori.service.import.ImportService
import com.dailysatori.service.mcp.McpAgentService
import com.dailysatori.service.memory.MemoryExtractService
import com.dailysatori.service.parser.WebpageParserService
import com.dailysatori.service.plugin.PluginService
import com.dailysatori.service.setting.SettingService
import com.dailysatori.service.weekly.WeeklySummaryService
import com.dailysatori.ui.feature.aichat.AiChatViewModel
import com.dailysatori.ui.feature.aichat.AiReferenceDetailViewModel
import com.dailysatori.ui.feature.aichat.MemorySearchViewModel
import com.dailysatori.ui.feature.aiconfig.AiConfigEditViewModel
import com.dailysatori.ui.feature.aiconfig.AiConfigViewModel
import com.dailysatori.ui.feature.article.ArticleDetailViewModel
import com.dailysatori.ui.feature.article.ArticlesViewModel
import com.dailysatori.ui.feature.settings.backup.BackupRestoreViewModel
import com.dailysatori.ui.feature.settings.backup.BackupSettingsViewModel
import com.dailysatori.ui.feature.settings.importing.DataImportViewModel
import com.dailysatori.ui.feature.book.BookSearchViewModel
import com.dailysatori.ui.feature.book.BookContentSearchViewModel
import com.dailysatori.ui.feature.book.BookReflectionViewModel
import com.dailysatori.ui.feature.book.BooksViewModel
import com.dailysatori.ui.feature.diary.DiaryViewModel
import com.dailysatori.ui.feature.settings.plugin.PluginCenterViewModel
import com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesSettingsViewModel
import com.dailysatori.ui.feature.settings.remotenews.RemoteNewsSettingsViewModel
import com.dailysatori.ui.feature.settings.SettingsViewModel
import com.dailysatori.ui.feature.settings.mcp.McpServerViewModel
import com.dailysatori.ui.feature.settings.skills.SkillSettingsViewModel
import com.dailysatori.ui.feature.share.ShareDialogViewModel
import com.dailysatori.ui.feature.remotenews.RemoteNewsViewModel
import com.dailysatori.ui.feature.settings.weekly.WeeklySummaryViewModel
import com.dailysatori.ui.feature.unifiednews.UnifiedNewsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

val viewModelModule: Module = module {
    viewModel {
        AppUrlIntakeViewModel(
            articleRepo = get<ArticleRepository>(),
            clipboardMonitorService = get(),
            articleProcessingScheduler = get(),
        )
    }
    viewModel {
        ArticlesViewModel(
            articleRepo = get<ArticleRepository>(),
            tagRepo = get<TagRepository>(),
            articleProcessingScheduler = get(),
        )
    }
    viewModel { params ->
        ArticleDetailViewModel(
            articleId = params.get<Long>(),
            articleRepo = get<ArticleRepository>(),
            tagRepo = get<TagRepository>(),
            memoryExtractService = get<MemoryExtractService>(),
            webpageParserService = get<WebpageParserService>(),
        )
    }
    viewModel {
        DiaryViewModel(
            diaryRepo = get<DiaryRepository>(),
            memoryExtractService = get<MemoryExtractService>(),
            monthSummaryRepo = get(),
            monthSummaryService = get(),
        )
    }
    viewModel {
        BooksViewModel(
            bookRepo = get<BookRepository>(),
            viewpointRepo = get<BookViewpointRepository>(),
            bookAiFallbackGenerator = get(),
        )
    }
    viewModel {
        BookSearchViewModel(
            bookIntelligenceService = get(),
            bookRepo = get<BookRepository>(),
            viewpointRepo = get<BookViewpointRepository>(),
        )
    }
    viewModel {
        BookContentSearchViewModel(
            viewpointRepo = get<BookViewpointRepository>(),
        )
    }
    viewModel {
        BookReflectionViewModel(
            reflectionRepo = get<BookViewpointAiRepository>(),
            reflectionService = get<BookReflectionService>(),
        )
    }
    viewModel {
        AiChatViewModel(
            mcpAgentService = get<McpAgentService>(),
            chatConversationRepo = get<ChatConversationRepository>(),
        )
    }
    viewModel {
        AiReferenceDetailViewModel(
            articleRepo = get<ArticleRepository>(),
            diaryRepo = get<DiaryRepository>(),
            bookRepo = get<BookRepository>(),
            viewpointRepo = get<BookViewpointRepository>(),
        )
    }
    viewModel {
        MemorySearchViewModel(
            memoryRepo = get(),
            extractService = get<MemoryExtractService>(),
            articleRepo = get<ArticleRepository>(),
            diaryRepo = get<DiaryRepository>(),
            bookRepo = get<BookRepository>(),
            viewpointRepo = get<BookViewpointRepository>(),
        )
    }
    viewModel {
        AiConfigViewModel(
            repo = get<AIConfigRepository>(),
        )
    }
    viewModel {
        AiConfigEditViewModel(
            repo = get<AIConfigRepository>(),
            aiService = get(),
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
            passwordStore = get(),
        )
    }
    viewModel {
        BackupRestoreViewModel(
            backupService = get<BackupService>(),
        )
    }
    viewModel {
        DataImportViewModel(
            importService = get<ImportService>(),
        )
    }
    viewModel {
        PluginCenterViewModel(
            pluginService = get<PluginService>(),
            settingRepo = get<SettingRepository>(),
        )
    }
    viewModel {
        UnifiedNewsViewModel(
            summaryRepo = get(),
            summaryService = get(),
            settingRepo = get<SettingRepository>(),
            remoteNewsService = get(),
            remoteNewsSourceRepo = get(),
            externalFavoriteSourceRepo = get(),
            articleRepo = get<ArticleRepository>(),
            webpageParserService = get<WebpageParserService>(),
            isDebugBuild = com.dailysatori.BuildConfig.DEBUG,
        )
    }
    viewModel {
        RemoteNewsViewModel(
            settingRepo = get<SettingRepository>(),
            remoteNewsService = get(),
            articleRepo = get<ArticleRepository>(),
            webpageParserService = get<WebpageParserService>(),
        )
    }
    viewModel {
        RemoteNewsSettingsViewModel(
            sourceRepo = get(),
            remoteNewsService = get(),
        )
    }
    viewModel {
        ExternalFavoritesSettingsViewModel(
            sourceRepo = get(),
            scheduler = get(),
            xOAuthCoordinator = get(),
            settingRepo = get<SettingRepository>(),
        )
    }
    viewModel {
        SettingsViewModel(
            webServerService = get<WebServerService>(),
            appUpgradeService = get<AppUpgradeService>(),
            settingRepo = get<SettingRepository>(),
        )
    }
    viewModel {
        McpServerViewModel(
            repo = get(),
            remoteMcpClient = get(),
        )
    }
    viewModel {
        SkillSettingsViewModel(
            repository = get(),
            connectionTester = get(),
        )
    }
}

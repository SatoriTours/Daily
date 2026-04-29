package com.dailysatori.di

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.ImageRepository
import com.dailysatori.data.repository.SessionRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.data.repository.WeeklySummaryRepository
import com.dailysatori.platform.FileManager
import com.dailysatori.platform.WebViewLoader
import com.dailysatori.service.adblock.AdBlockService
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.backup.BackupService
import com.dailysatori.service.book.BookSearchService
import com.dailysatori.service.book.GoogleBooksSearchEngine
import com.dailysatori.service.book.OpenLibrarySearchEngine
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.import.ImportService
import com.dailysatori.service.mcp.McpAgentService
import com.dailysatori.service.parser.WebpageParserService
import com.dailysatori.service.plugin.PluginService
import com.dailysatori.service.setting.SettingService
import com.dailysatori.service.weekly.WeeklySummaryService
import org.koin.core.module.Module
import org.koin.dsl.module

val sharedModule: Module = module {
    // HttpClient (platform-specific)
    single { createHttpClient() }

    // Repositories
    single { ArticleRepository(get()) }
    single { AIConfigRepository(get()) }
    single { BookRepository(get()) }
    single { BookViewpointRepository(get()) }
    single { DiaryRepository(get()) }
    single { ImageRepository(get()) }
    single { SessionRepository(get()) }
    single { SettingRepository(get()) }
    single { TagRepository(get()) }
    single { WeeklySummaryRepository(get()) }

    // Core services
    single { SettingService(get()) }
    single { I18nService(get()) }
    single { AiConfigService(get()) }
    single { AiService(get()) }
    single { BackupService(get(), get()) }
    single { PluginService(get(), get()) }

    // AdBlock service (loads EasyList rules from assets via FileManager)
    single {
        val rulesText = try {
            get<FileManager>().readAssetText("easylistchina+easylist.txt")
        } catch (_: Exception) {
            ""
        }
        AdBlockService(rulesText)
    }

    // Webpage parser service (content processing pipeline)
    single { WebpageParserService(get(), get(), get(), get(), get(), get(), get(), get()) }

    // Book search engines
    single { GoogleBooksSearchEngine(get()) }
    single { OpenLibrarySearchEngine(get()) }
    single { BookSearchService(listOf(get<GoogleBooksSearchEngine>(), get<OpenLibrarySearchEngine>())) }

    // Weekly summary service
    single { WeeklySummaryService(get(), get(), get(), get(), get(), get()) }

    // Import service
    single { ImportService(get(), get(), get()) }

    // MCP Agent service
    single { McpAgentService(get(), get(), get(), get(), get(), get()) }
}

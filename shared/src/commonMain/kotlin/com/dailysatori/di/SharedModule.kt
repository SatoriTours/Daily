package com.dailysatori.di

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointAiRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.ChatConversationRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryMonthSummaryRepository
import com.dailysatori.data.repository.ImageRepository
import com.dailysatori.data.repository.McpServerRepository
import com.dailysatori.data.repository.MemoryRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.data.repository.SessionRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.data.repository.SkillConfigDataSource
import com.dailysatori.data.repository.SkillConfigRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.data.repository.UnifiedNewsSummaryRepository
import com.dailysatori.data.repository.WeeklySummaryRepository
import com.dailysatori.platform.FileManager
import com.dailysatori.platform.WebViewLoader
import com.dailysatori.service.adblock.AdBlockService
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.backup.BackupService
import com.dailysatori.service.backup.BackupPasswordStore
import com.dailysatori.service.book.BookIntelligenceService
import com.dailysatori.service.book.BookIntelligenceSource
import com.dailysatori.service.book.BookSearchService
import com.dailysatori.service.book.BookAiFallbackGenerator
import com.dailysatori.service.book.DefaultBookAiFallbackGenerator
import com.dailysatori.service.book.DoubanSuggestSearchEngine
import com.dailysatori.service.book.WeReadSkillService
import com.dailysatori.service.book.WebSearchEngine
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.import.ImportService
import com.dailysatori.service.mcp.AiSearchOrchestrator
import com.dailysatori.service.mcp.McpAgentService
import com.dailysatori.service.mcp.LocalSqlQueryService
import com.dailysatori.service.mcp.McpToolRegistry
import com.dailysatori.service.mcp.RemoteMcpClient
import com.dailysatori.service.memory.MemoryExtractService
import com.dailysatori.service.migration.DatabaseMigration
import com.dailysatori.service.parser.WebpageParserService
import com.dailysatori.service.plugin.PluginService
import com.dailysatori.service.crayfishnews.CrayfishNewsService
import com.dailysatori.service.diary.DiaryMonthSummaryService
import com.dailysatori.service.remotenews.RemoteNewsService
import com.dailysatori.service.security.SecretCipher
import com.dailysatori.service.setting.SettingService
import com.dailysatori.service.skill.DefaultSkillConnectionTester
import com.dailysatori.service.skill.SkillConnectionTester
import com.dailysatori.service.skill.SkillRegistry
import com.dailysatori.service.unifiednews.UnifiedNewsSummaryService
import com.dailysatori.service.weekly.WeeklySummaryService
import org.koin.core.module.Module
import org.koin.dsl.module

val sharedModule: Module = module {
    // HttpClient (platform-specific)
    single { createHttpClient() }

    // Repositories
    single { ArticleRepository(get()) }
    single { AIConfigRepository(get(), get()) }
    single { BookRepository(get()) }
    single { BookViewpointAiRepository(get()) }
    single { BookViewpointRepository(get()) }
    single { ChatConversationRepository(get()) }
    single { DiaryRepository(get()) }
    single { DiaryMonthSummaryRepository(get()) }
    single { ImageRepository(get()) }
    single { MemoryRepository(get()) }
    single { RemoteNewsSourceRepository(get(), get()) }
    single { SessionRepository(get()) }
    single { SettingRepository(get()) }
    single { SkillConfigRepository(get(), get()) }
    single<SkillConfigDataSource> { get<SkillConfigRepository>() }
    single { TagRepository(get()) }
    single { UnifiedNewsSummaryRepository(get()) }
    single { WeeklySummaryRepository(get()) }

    // Core services
    single { SettingService(get()) }
    single { I18nService(get()) }
    single { AiConfigService(get()) }
    single { AiService(get()) }
    single { BackupPasswordStore(get()) }
    single { SecretCipher(get()) }
    single { BackupService(get(), get(), get()) }
    single { PluginService(get(), get()) }
    single { RemoteNewsService(get()) }
    single { CrayfishNewsService(get()) }
    single { DiaryMonthSummaryService(get(), get(), get(), get()) }
    single { MemoryExtractService(get(), get(), get()) }
    single { UnifiedNewsSummaryService(get(), get(), get(), get(), get(), get()) }
    single { SkillRegistry(get()) }
    single<SkillConnectionTester> { DefaultSkillConnectionTester(get()) }

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

    // Book search service
    single { DoubanSuggestSearchEngine(get()) }
    single { WebSearchEngine(get()) }
    single { BookSearchService(listOf(get<DoubanSuggestSearchEngine>(), get<WebSearchEngine>())) }
    single<BookAiFallbackGenerator> { DefaultBookAiFallbackGenerator(get(), get(), get(), get()) }
    single { WeReadSkillService(get(), get(), get(), get(), get()) }
    single<BookIntelligenceSource> { get<WeReadSkillService>() }
    single { RemoteMcpClient(get()) }
    single { LocalSqlQueryService(get()) }
    single { BookIntelligenceService(get<BookIntelligenceSource>()) }

    // Weekly summary service
    single { WeeklySummaryService(get(), get(), get(), get(), get(), get()) }

    // MCP server config
    single { McpServerRepository(get(), get()) }

    // Migration
    single { DatabaseMigration(get(), get(), get()) }

    // Import service
    single { ImportService(get(), get(), get()) }

    // MCP Tool registry
    single { McpToolRegistry(get(), get(), get(), get(), get(), get(), get(), get()) }

    // MCP Agent service
    single { AiSearchOrchestrator(get(), get(), get(), get(), get()) }
    single { McpAgentService(get(), get(), get(), get()) }
}

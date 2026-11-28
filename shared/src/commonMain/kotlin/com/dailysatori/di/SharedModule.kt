package com.dailysatori.di

import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointAiRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.ChatConversationRepository
import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.data.repository.IdeaTopicRepository
import com.dailysatori.data.repository.ImageRepository
import com.dailysatori.data.repository.McpServerRepository
import com.dailysatori.data.repository.MemoryRepository
import com.dailysatori.data.repository.ReminderAiBatchRepository
import com.dailysatori.data.repository.ReminderRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.data.repository.SessionRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.data.repository.SkillConfigDataSource
import com.dailysatori.data.repository.SkillConfigRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.data.repository.UnifiedNewsSummaryRepository
import com.dailysatori.service.localnews.LocalNewsBriefingReader
import com.dailysatori.service.localnews.RepositoryLocalNewsBriefingReader
import com.dailysatori.data.repository.WeeklySummaryRepository
import com.dailysatori.platform.FileManager
import com.dailysatori.service.adblock.AdBlockService
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiModelCatalogService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.backup.BackupPasswordStore
import com.dailysatori.service.backup.BackupService
import com.dailysatori.service.book.BookAiFallbackGenerator
import com.dailysatori.service.book.BookIntelligenceService
import com.dailysatori.service.book.BookIntelligenceSource
import com.dailysatori.service.book.BookReflectionService
import com.dailysatori.service.book.BookSearchService
import com.dailysatori.service.book.DefaultBookAiFallbackGenerator
import com.dailysatori.service.book.DoubanSuggestSearchEngine
import com.dailysatori.service.book.WeReadSkillService
import com.dailysatori.service.book.WebSearchEngine
import com.dailysatori.service.crayfishnews.CrayfishNewsService
import com.dailysatori.service.externalfavorites.DefaultExternalFavoriteSupplementResolver
import com.dailysatori.service.externalfavorites.ExternalFavoriteAiOrganizer
import com.dailysatori.service.externalfavorites.ExternalFavoriteImporter
import com.dailysatori.service.externalfavorites.ExternalFavoriteSupplementResolver
import com.dailysatori.service.externalfavorites.FavoriteConnectorRegistry
import com.dailysatori.service.externalfavorites.FavoriteSyncService
import com.dailysatori.service.externalfavorites.GitHubStarsConnector
import com.dailysatori.service.externalfavorites.NoopFavoriteSyncHttpLogger
import com.dailysatori.service.externalfavorites.XBookmarksConnector
import com.dailysatori.service.hotnews.HotNewsClient
import com.dailysatori.service.hotnews.HotNewsService
import com.dailysatori.service.hotnews.HotNewsStore
import com.dailysatori.service.hotnews.HotNewsTranslator
import com.dailysatori.service.hotnews.XHotNewsClient
import com.dailysatori.service.hotnews.xHotNewsAuthorization
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.import.ImportService
import com.dailysatori.service.mcp.AiSearchOrchestrator
import com.dailysatori.service.mcp.LocalSqlQueryService
import com.dailysatori.service.mcp.McpAgentService
import com.dailysatori.service.mcp.McpToolRegistry
import com.dailysatori.service.mcp.RemoteMcpClient
import com.dailysatori.service.memory.MemoryExtractService
import com.dailysatori.service.memory.MemoryExtractor
import com.dailysatori.service.migration.DatabaseMigration
import com.dailysatori.service.parser.WebpageParserService
import com.dailysatori.service.plugin.PluginService
import com.dailysatori.service.reminder.ReminderAiInterpretationRemote
import com.dailysatori.service.reminder.ReminderBatchCodec
import com.dailysatori.service.reminder.ReminderDraftCodec
import com.dailysatori.service.reminder.ReminderTextInterpreter
import com.dailysatori.service.remotenews.RemoteArticleFavoriteService
import com.dailysatori.service.remotenews.RemoteFeedSourceSyncService
import com.dailysatori.service.remotenews.RemoteNewsService
import com.dailysatori.service.security.SecretCipher
import com.dailysatori.service.security.SecretFieldProcessor
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.service.setting.SettingService
import com.dailysatori.service.skill.DefaultSkillConnectionTester
import com.dailysatori.service.skill.SkillConnectionTester
import com.dailysatori.service.skill.SkillRegistry
import com.dailysatori.service.unifiednews.UnifiedNewsSummaryService
import com.dailysatori.service.weekly.WeeklySummaryService
import org.koin.core.module.Module
import org.koin.dsl.module

val sharedModule: Module = module {
    includes(diaryModule, newsOpportunityModule, articleProcessingModule)

    // HttpClient (platform-specific)
    single { createHttpClient() }

    // Repositories
    single { AIConfigRepository(get()) }
    single { AsyncTaskRepository(get()) }
    single { BookRepository(get()) }
    single { BookViewpointAiRepository(get()) }
    single { BookViewpointRepository(get()) }
    single { ChatConversationRepository(get(),com.dailysatori.service.assistant.AssistantPrivacyRepository(get())) }
    single { com.dailysatori.service.assistant.AssistantTurnRepository(get()) }
    single { com.dailysatori.service.assistant.AssistantPrivacyRepository(get()) }
    single { com.dailysatori.service.assistant.data.AssistantDataGateway(get()) }
    single { com.dailysatori.service.assistant.KoogReadOnlyAssistant(get(), get(), get(), get(), get()) }
    single { com.dailysatori.service.assistant.AssistantMemoryService(get()) }
    single { com.dailysatori.service.assistant.AssistantMemoryIndexer(get(), get(), get<MemoryExtractService>()::extractPersonalCandidates) }
    single { com.dailysatori.service.assistant.AssistantIndexTaskHandler(get(), get()) }
    single { com.dailysatori.service.assistant.AssistantHistoryBackfill(get()) }
    single { com.dailysatori.service.assistant.AssistantHistoryBackfillTaskHandler(get()) }
    single { com.dailysatori.service.assistant.AssistantConversationSummarizer(get(), summarize = get<MemoryExtractService>()::summarizeConversation) }
    single { com.dailysatori.service.assistant.AssistantSummaryTaskHandler(get()) }
    single { com.dailysatori.service.assistant.AssistantActionService(get(), get()) }
    single { com.dailysatori.service.assistant.KoogAssistantRuntime(get(), get(), get(), get(), get(), get()) }
    single { com.dailysatori.service.assistant.AssistantTurnTaskHandler(get(), get(), get<com.dailysatori.service.assistant.KoogAssistantRuntime>()) }
    single { ExternalFavoriteSourceRepository(get()) }
    single { ExternalFavoriteItemRepository(get()) }
    single { ImageRepository(get()) }
    single { IdeaTopicRepository(get()) }
    single { com.dailysatori.service.ideatopic.IdeaTopicService(get()) }
    single<com.dailysatori.service.ideatopic.IdeaTopicAiPort> {
        com.dailysatori.service.ideatopic.IdeaTopicAiService(get(), get(), get())
    }
    single { com.dailysatori.service.ideatopic.IdeaTopicAiWorkflow(get(), get()) }
    single { MemoryRepository(get()) }
    single { RemoteArticleSyncRepository(get()) }
    single { ReminderRepository(get()) }
    single { ReminderAiBatchRepository(get()) }
    single { com.dailysatori.data.repository.SmsSourceRepository(get()) }
    single { com.dailysatori.data.repository.BookkeepingRepository(get()) }
    single { com.dailysatori.service.bookkeeping.BookkeepingService(get(), get()) }
    single<com.dailysatori.service.sms.SmsReminderRemote> { com.dailysatori.service.sms.SmsReminderAi(get(), get()) }
    single { com.dailysatori.service.sms.SmsReminderService(get(), get(), get(), get()) }
    single { com.dailysatori.data.repository.PhoneMessageRepository(get()) }
    single { com.dailysatori.service.phone.PhoneAssistantService(get(), get(), get(), get(), get()) }
    single { RemoteNewsSourceRepository(get()) }
    single { SessionRepository(get()) }
    single { SettingRepository(get()) }
    single { SkillConfigRepository(get()) }
    single<SkillConfigDataSource> { get<SkillConfigRepository>() }
    single { TagRepository(get()) }
    single { UnifiedNewsSummaryRepository(get()) }
    single<LocalNewsBriefingReader> { RepositoryLocalNewsBriefingReader(get()) }
    single { WeeklySummaryRepository(get()) }

    // Core services
    single { SettingService(get()) }
    single { I18nService(get()) }
    single { AiConfigService(get()) }
    single { com.dailysatori.service.ai.AiRequestCoordinator(get()) }
    single { AiService(get(), get()) }
    single { com.dailysatori.service.ai.AiConversationSessionStore(get()) }
    single { AiModelCatalogService(get(), get()) }
    single { BackupPasswordStore(get()) }
    single { SecretCipher(get()) }
    single<SecretValueCipher> { get<SecretCipher>() }
    single { SecretFieldProcessor(get(), get<SecretCipher>()) }
    single { BackupService(get(), get(), get(), get(), get<SecretCipher>(), get()) }
    single { PluginService(get(), get()) }
    single { RemoteNewsService(get()) }
    single { CrayfishNewsService(get()) }
    single { XBookmarksConnector(get(), sourceRepository = get()) }
    single { GitHubStarsConnector(get()) }
    single { FavoriteConnectorRegistry(listOf(get<XBookmarksConnector>(), get<GitHubStarsConnector>())) }
    single { ExternalFavoriteImporter(get(), get()) }
    single<ExternalFavoriteSupplementResolver> {
        DefaultExternalFavoriteSupplementResolver(
            sourceRepo = get(),
            xBookmarksConnector = get(),
            webpageParserService = get(),
        )
    }
    single { ExternalFavoriteAiOrganizer(get(), get(), get(), get(), get(), settingRepo = get(), sourceRepo = get()) }
    single { FavoriteSyncService(get(), get(), get(), get(), get(), httpLogger = getOrNull() ?: NoopFavoriteSyncHttpLogger) }
    single { RemoteArticleFavoriteService(get(), get()) }
    single { com.dailysatori.data.repository.RemoteFeedSyncStateRepository(get()) }
    single { com.dailysatori.data.repository.RemoteFeedHistoryRepository(get()) }
    single {
        com.dailysatori.service.remotenews.RemoteFeedHistoryImportService(
            get(), get(), get(), get(),
        )
    }
    single { RemoteFeedSourceSyncService(get(), get(), get(), get()) }
    single { MemoryExtractService(get(), get(), get()) }
    single<MemoryExtractor> { get<MemoryExtractService>() }
    single {
        val sources: ExternalFavoriteSourceRepository = get()
        val connector: XBookmarksConnector = get()
        XHotNewsClient(get(), { xHotNewsAuthorization(sources, connector) })
    }
    single { HotNewsClient(get(), get<XHotNewsClient>()::fetch) }
    single { HotNewsStore(get()) }
    single { HotNewsTranslator(get(), get()) }
    single {
        val translator: HotNewsTranslator = get()
        val parser: WebpageParserService = get()
        HotNewsService(get(), get(), translator::translate) { url ->
            com.dailysatori.service.parser.articleOriginalMarkdown(parser.extractContent(url), url)
        }
    }
    single { UnifiedNewsSummaryService(get(), get(), get(), get(), get(), get(), get()) }
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

    // Book search service
    single { DoubanSuggestSearchEngine(get()) }
    single { WebSearchEngine(get()) }
    single { BookSearchService(listOf(get<DoubanSuggestSearchEngine>(), get<WebSearchEngine>())) }
    single<BookAiFallbackGenerator> { DefaultBookAiFallbackGenerator(get(), get(), get(), get()) }
    single { WeReadSkillService(get(), get(), get(), get()) }
    single<BookIntelligenceSource> { get<WeReadSkillService>() }
    single { BookReflectionService(get(), get(), get()) }
    single { RemoteMcpClient(get()) }
    single { LocalSqlQueryService(get()) }
    single { BookIntelligenceService(get<BookIntelligenceSource>()) }

    // Weekly summary service
    single { WeeklySummaryService(get(), get(), get(), get(), get(), get()) }

    // MCP server config
    single { McpServerRepository(get()) }

    // Migration
    single { DatabaseMigration(get(), get()) }

    // Import service
    single { ImportService(get(), get(), get()) }

    // MCP Tool registry
    single { ReminderDraftCodec() }
    single { ReminderBatchCodec(get()) }
    single { ReminderAiInterpretationRemote(get(), get()) }
    single { ReminderTextInterpreter(get(), get<ReminderAiInterpretationRemote>()) }
    single { McpToolRegistry(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    // MCP Agent service
    single { AiSearchOrchestrator(get(), get(), get(), get(), get()) }
    single { McpAgentService(get(), get(), get(), get(), get()) }
}

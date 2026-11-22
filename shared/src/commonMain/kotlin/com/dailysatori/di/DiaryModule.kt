package com.dailysatori.di

import com.dailysatori.data.repository.DiaryAttachmentRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryMonthSummaryRepository
import com.dailysatori.data.repository.DiaryThoughtRepository
import com.dailysatori.service.diary.DiaryThoughtGenerator
import com.dailysatori.service.diary.DiaryThoughtService
import com.dailysatori.service.diary.DiaryThoughtChatContextProvider
import com.dailysatori.data.repository.McpServerRepository
import com.dailysatori.service.ai.AiPurpose
import com.dailysatori.service.mcp.RemoteMcpClient
import com.dailysatori.service.memory.MemoryExtractor
import com.dailysatori.service.diary.DiaryKnowledgeCoordinator
import com.dailysatori.service.diary.DiaryKnowledgeEnricher
import com.dailysatori.service.diary.DiaryAssistantService
import com.dailysatori.service.diary.DiaryTranscriptPolishService
import com.dailysatori.service.diary.DiaryTitleGenerator
import com.dailysatori.service.diary.diaryTitleCompletion
import com.dailysatori.service.diary.diaryTranscriptPolishCompletion
import com.dailysatori.service.diary.DiaryTranscriptionCoordinator
import com.dailysatori.service.diary.DiaryLinkContentExtractor
import com.dailysatori.service.diary.DefaultDiaryLinkContentExtractor
import com.dailysatori.service.diary.OpenAiCompatibleSpeechTranscriptionClient
import com.dailysatori.service.diary.SpeechTranscriptionClient
import com.dailysatori.service.diary.SpeechSettingsService
import com.dailysatori.service.diary.SpeechTranscriptionApi
import com.dailysatori.service.diary.diaryAssistantCompletion
import com.dailysatori.service.parser.WebpageParserService
import com.dailysatori.service.diary.DiaryMonthSummaryService
import org.koin.dsl.module

internal val diaryModule = module {
    single { DiaryAttachmentRepository(get(), get(), get()) }
    single { DiaryRepository(get(), get(), get()) }
    single { com.dailysatori.data.repository.DiaryThreadRepository(get(), get()) }
    single { com.dailysatori.data.repository.DiaryTagRepository(get(), get()) }
    single {
        val configs = get<com.dailysatori.service.ai.AiConfigService>()
        val ai = get<com.dailysatori.service.ai.AiService>()
        com.dailysatori.service.diary.DiaryTagGenerator { prompt, system ->
            val config = configs.getConfig(AiPurpose.INTERACTIVE) ?: error("请先配置默认 AI")
            ai.completePrivate(prompt, config.api_address, config.api_token, config.model_name, config.provider, system,
                purpose = AiPurpose.INTERACTIVE)
        }
    }
    single {
        val configs = get<com.dailysatori.service.ai.AiConfigService>()
        com.dailysatori.service.diary.DiaryTagCoordinator(get(), get(), get(), get()) {
            configs.getConfig(AiPurpose.INTERACTIVE)?.let { it.api_token.isNotBlank() && it.api_address.isNotBlank() && it.model_name.isNotBlank() } == true
        }
    }
    single { DiaryMonthSummaryRepository(get()) }
    single { DiaryThoughtRepository(get()) }
    single { DiaryThoughtGenerator(diaryAssistantCompletion(get(), get(), AiPurpose.REFLECTION)) }
    single { DiaryThoughtService(get(), get(), get()) }
    single { DiaryThoughtChatContextProvider(get(), get()) }
    single {
        val configs = get<com.dailysatori.service.ai.AiConfigService>()
        val ai = get<com.dailysatori.service.ai.AiService>()
        com.dailysatori.service.diary.DiaryThreadSummaryGenerator { prompt, system ->
            val config = configs.getConfig(AiPurpose.INTERACTIVE) ?: error("请先配置默认 AI")
            ai.completePrivate(prompt, config.api_address, config.api_token, config.model_name, config.provider, system,
                purpose = AiPurpose.INTERACTIVE)
        }
    }
    single {
        val configs = get<com.dailysatori.service.ai.AiConfigService>()
        com.dailysatori.service.diary.DiaryThreadSummaryCoordinator(get(), get(), get()) {
            configs.getConfig(AiPurpose.INTERACTIVE)?.let { it.api_token.isNotBlank() && it.api_address.isNotBlank() && it.model_name.isNotBlank() } == true
        }
    }
    single { DiaryMonthSummaryService(get(), get(), get(), get(), get()) }
    single { DiaryKnowledgeCoordinator(get(), get(), get<MemoryExtractor>(), get()) }
    single { SpeechSettingsService(get(), get()) }
    single { SpeechTranscriptionApi(get()) }
    single<SpeechTranscriptionClient> { OpenAiCompatibleSpeechTranscriptionClient(get(), get(), get()) }
    single { DiaryTitleGenerator(diaryTitleCompletion(get(), get())) }
    single { DiaryTranscriptionCoordinator(get(), get(), get(), get(), get(), get()) }
    single<DiaryLinkContentExtractor> { DefaultDiaryLinkContentExtractor(get<WebpageParserService>()) }
    single<DiaryKnowledgeEnricher> {
        val mcpServers: McpServerRepository = get()
        val remoteMcp: RemoteMcpClient = get()
        val complete: suspend (String, String) -> String = diaryAssistantCompletion(get(), get())
        DiaryKnowledgeEnricher(
            collectWebNotes = { query ->
                remoteMcp.collectWebSearchNotes(
                    mcpServers.getEnabled().filter { it.server_url.startsWith("http", ignoreCase = true) },
                    query,
                )
            },
            complete = complete,
        )
    }
    single<DiaryAssistantService> {
        val complete: suspend (String, String) -> String = diaryAssistantCompletion(get(), get(), AiPurpose.EXTERNAL_CONTENT)
        DiaryAssistantService(get(), get(), complete)
    }

    single { DiaryTranscriptPolishService(diaryTranscriptPolishCompletion(get(), get())) }
}

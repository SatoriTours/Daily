package com.dailysatori.di

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.service.parser.WebpageParserService
import com.dailysatori.service.remotenews.RemoteArticleSyncService
import org.koin.dsl.module

internal val articleProcessingModule = module {
    single { ArticleRepository(get()) }
    single { com.dailysatori.data.repository.ArticleAiRepository(get(), get()) }
    single<com.dailysatori.service.articleai.ArticleAiRemote> {
        com.dailysatori.service.articleai.ConfiguredArticleAiRemote(get(), get())
    }
    single {
        val queue = getOrNull<com.dailysatori.service.articleai.ArticleAiQueue>()
        RemoteArticleSyncService(get(), get(), get()) { article -> queue?.onNewBody(article) }
    }
    // Webpage parser service (content processing pipeline)
    single { WebpageParserService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
        settingRepo = get(), coverScheduler = get(), externalFavoriteItemRepo = get()) }
}

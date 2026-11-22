package com.dailysatori.di

import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.service.opportunity.AiNewsOpportunityAnalyzer
import com.dailysatori.service.opportunity.DiaryThoughtOpportunityContext
import com.dailysatori.service.opportunity.NewsOpportunityContext
import com.dailysatori.service.opportunity.NewsOpportunityService
import com.dailysatori.service.opportunity.NewsOpportunityStore
import com.dailysatori.service.opportunity.OpportunityAnalyzer
import org.koin.dsl.module

internal val newsOpportunityModule = module {
    single { NewsOpportunityStore(get()) }
    single<OpportunityAnalyzer> { AiNewsOpportunityAnalyzer(get(), get()) }
    single<NewsOpportunityContext> { DiaryThoughtOpportunityContext(get(), get()) }
    single<com.dailysatori.service.opportunity.OpportunityCandidateSource> {
        com.dailysatori.service.opportunity.NewsOpportunityCandidateSource(get(), get<RemoteNewsSourceRepository>()::getEnabled, get(), get(), get())
    }
    single { NewsOpportunityService(get(), get(), get(), get()) }
}

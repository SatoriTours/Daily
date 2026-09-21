package com.dailysatori.service.opportunity

import com.dailysatori.data.repository.DiaryThoughtRepository
import com.dailysatori.service.diary.DiaryThoughtChatContextProvider

class DiaryThoughtOpportunityContext(
    private val repository: DiaryThoughtRepository,
    private val provider: DiaryThoughtChatContextProvider,
) : NewsOpportunityContext {
    override val enabled: Boolean get() = repository.useInChat()

    override fun verifiedContext(): String? {
        if (!enabled) return null
        return provider.getContext()?.prompt
    }
}


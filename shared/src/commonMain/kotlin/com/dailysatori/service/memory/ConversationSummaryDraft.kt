package com.dailysatori.service.memory

import kotlinx.serialization.Serializable

/** The host supplies identities and validates every new quote; the model cannot assign sources. */
@Serializable
data class ConversationSummaryDraft(val summary: String, val quotes: List<String> = emptyList())

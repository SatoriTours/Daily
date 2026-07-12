package com.dailysatori.service.ai

import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.shared.db.Ai_config

class AiConfigService(private val repo: AIConfigRepository) {
    fun getDefaultConfig(): Ai_config? = repo.getDefault()
    fun getById(id: Long) = repo.getById(id)
    fun delete(id: Long) = repo.delete(id)

    fun getSpeechConfig(): Ai_config? {
        val configs = repo.getAllSync()
        val default = configs.firstOrNull { it.is_default == 1L }
        return default?.takeIf { it.provider.supportsSpeechInput() }
            ?: configs.firstOrNull { it.provider.supportsSpeechInput() }
    }
}

private fun String.supportsSpeechInput(): Boolean =
    lowercase() in setOf("openai", "gemini", "google", "google-gemini")

package com.dailysatori.service.ai

import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.shared.db.Ai_config

class AiConfigService(private val repo: AIConfigRepository) {
    fun getDefaultConfig(): Ai_config? = repo.getDefault()
    fun getById(id: Long) = repo.getById(id)
    fun delete(id: Long) = repo.delete(id)
}

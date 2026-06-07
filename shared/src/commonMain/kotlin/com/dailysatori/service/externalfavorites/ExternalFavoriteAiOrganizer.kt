package com.dailysatori.service.externalfavorites

import com.dailysatori.data.repository.ExternalFavoriteItemRepository

class ExternalFavoriteAiOrganizer(private val itemRepo: ExternalFavoriteItemRepository) {
    fun organizePending(limit: Long = 10): Int = 0
}

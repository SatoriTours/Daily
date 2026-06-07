package com.dailysatori.service.externalfavorites

import com.dailysatori.shared.db.External_favorite_source

interface FavoriteConnector {
    val provider: String
    val capabilities: FavoriteConnectorCapabilities

    suspend fun fetchPage(
        source: External_favorite_source,
        cursor: String? = null,
        pageSize: Int = capabilities.maxPageSize,
    ): FavoriteFetchPage
}

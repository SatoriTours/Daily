package com.dailysatori.service.externalfavorites

import com.dailysatori.shared.db.External_favorite_source

typealias FavoriteFetchDetailPolicy = (ExternalFavoriteItemDraft) -> Boolean

interface FavoriteConnector {
    val provider: String
    val capabilities: FavoriteConnectorCapabilities

    suspend fun refreshAuth(source: External_favorite_source): External_favorite_source = source

    suspend fun probePage(
        source: External_favorite_source,
        pageSize: Int,
        httpLogger: FavoriteSyncHttpLogger = NoopFavoriteSyncHttpLogger,
        taskId: Long? = null,
    ): FavoriteFetchPage = fetchPage(
        source = source,
        pageSize = pageSize,
        httpLogger = httpLogger,
        taskId = taskId,
        shouldFetchDetail = { false },
    )

    suspend fun fetchPage(
        source: External_favorite_source,
        cursor: String? = null,
        pageSize: Int = capabilities.maxPageSize,
        httpLogger: FavoriteSyncHttpLogger = NoopFavoriteSyncHttpLogger,
        taskId: Long? = null,
        shouldFetchDetail: FavoriteFetchDetailPolicy = { true },
        sinceExternalId: String? = null,
    ): FavoriteFetchPage
}

package com.dailysatori.service.externalfavorites

class FavoriteConnectorRegistry(connectors: List<FavoriteConnector>) {
    private val byProvider = connectors.associateBy { it.provider }

    fun get(provider: String): FavoriteConnector? = byProvider[provider]

    companion object {
        fun default(): FavoriteConnectorRegistry = FavoriteConnectorRegistry(
            listOf(XBookmarksConnector()),
        )
    }
}

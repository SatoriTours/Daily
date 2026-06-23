package com.dailysatori.service.externalfavorites

interface FavoriteSyncHttpLogger {
    fun logRequest(
        taskId: Long?,
        label: String,
        method: String,
        url: String,
        parameters: Map<String, String>,
    )

    fun logResponse(
        taskId: Long?,
        label: String,
        statusCode: Int,
        headers: Map<String, String>,
        body: String,
    )
}

object NoopFavoriteSyncHttpLogger : FavoriteSyncHttpLogger {
    override fun logRequest(
        taskId: Long?,
        label: String,
        method: String,
        url: String,
        parameters: Map<String, String>,
    ) = Unit

    override fun logResponse(
        taskId: Long?,
        label: String,
        statusCode: Int,
        headers: Map<String, String>,
        body: String,
    ) = Unit
}

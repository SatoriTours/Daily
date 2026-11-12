package com.dailysatori.service.externalfavorites

interface FavoriteSyncHttpLogger {
    fun logRequest(
        taskId: Long?,
        label: String,
        method: String,
        url: String,
        parameters: Map<String, String>,
        /** Explicit identity of this call, shared by its response. Null means the pairing is unknown. */
        requestId: String? = null,
        /** 1 for the first send of this identity; a retried send is a new identity. */
        attempt: Int? = null,
    )

    fun logResponse(
        taskId: Long?,
        label: String,
        statusCode: Int,
        headers: Map<String, String>,
        body: String,
        /** Must be the same value passed to logRequest; null means the pairing is unknown. */
        requestId: String? = null,
        attempt: Int? = null,
    )
}

object NoopFavoriteSyncHttpLogger : FavoriteSyncHttpLogger {
    override fun logRequest(
        taskId: Long?,
        label: String,
        method: String,
        url: String,
        parameters: Map<String, String>,
        requestId: String?,
        attempt: Int?,
    ) = Unit

    override fun logResponse(
        taskId: Long?,
        label: String,
        statusCode: Int,
        headers: Map<String, String>,
        body: String,
        requestId: String?,
        attempt: Int?,
    ) = Unit
}

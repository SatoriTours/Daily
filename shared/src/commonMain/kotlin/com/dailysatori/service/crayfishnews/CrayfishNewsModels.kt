package com.dailysatori.service.crayfishnews

import kotlinx.serialization.Serializable

data class CrayfishNewsConfigValues(
    val baseUrl: String,
    val token: String,
)

sealed class CrayfishNewsResult<out T> {
    data class Success<T>(val value: T) : CrayfishNewsResult<T>()
    data class Failure(val message: String) : CrayfishNewsResult<Nothing>()
}

@Serializable
data class CrayfishNewsDetail(
    val filename: String = "",
    val generated: String? = null,
    val source: String? = null,
    val sections: Map<String, String> = emptyMap(),
    val content: String = "",
    val preview: String = "",
)

@Serializable
data class CrayfishNewsListItem(
    val filename: String = "",
    val generated: String? = null,
    val source: String? = null,
    val preview: String = "",
)

@Serializable
data class CrayfishNewsListResponse(
    val general: List<CrayfishNewsListItem> = emptyList(),
    val dji: List<CrayfishNewsListItem> = emptyList(),
)

@Serializable
data class CrayfishHealthResponse(
    val status: String = "",
    val user: String = "",
    val ts: String = "",
)

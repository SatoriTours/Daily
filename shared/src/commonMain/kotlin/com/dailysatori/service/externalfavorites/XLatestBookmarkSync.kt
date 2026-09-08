package com.dailysatori.service.externalfavorites

import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*

/** Latest scans and history backfill own separate cursors. Neither resets the other. */
internal class XLatestBookmarkSync(
    private val sources: ExternalFavoriteSourceRepository,
    private val items: ExternalFavoriteItemRepository,
    private val connector: FavoriteConnector,
    private val logger: FavoriteSyncHttpLogger,
    private val shouldFetchDetail: (Long, ExternalFavoriteItemDraft) -> Boolean,
    private val stopAtKnownItem: Boolean = false,
) {
    suspend fun run(
        sourceId: Long,
        taskId: Long?,
        maxPages: Int,
        maxItems: Int,
        onProgress: suspend (FavoriteSyncProgress) -> Unit,
    ): XLatestSyncResult {
        val state = ScanState(readConfig(sourceId))
        if (state.cursor == null) state.order = maxOf(Clock.System.now().toEpochMilliseconds(), items.favoriteOrderStateBySource(sourceId).newest ?: 0) + maxItems + 1
        val totals = XLatestSyncResult()
        var knownPages = 0
        var finished = false
        while (totals.pages < maxPages && totals.seen < maxItems && !finished) {
            val page = connector.fetchPage(
                source = sources.getById(sourceId) ?: error("收藏来源不存在"),
                cursor = state.cursor,
                pageSize = minOf(20, maxItems - totals.seen, connector.capabilities.maxPageSize),
                httpLogger = logger, taskId = taskId,
                shouldFetchDetail = { draft -> shouldFetchDetail(sourceId, draft) },
                sinceExternalId = null,
            ).let { it.copy(items = it.items.take(maxItems - totals.seen)) }
            require(page.nextCursor == null || page.nextCursor != state.cursor) {
                "分页位置未前进，进度已保留，请稍后重试或使用修复同步"
            }
            val known = page.items.count { items.getBySourceExternalId(sourceId, it.externalId) != null }
            knownPages = if (page.items.isNotEmpty() && known == page.items.size) knownPages + 1 else 0
            val reachedBoundary = page.items.count { it.externalId in state.boundary } >= minOf(3, state.boundary.size).coerceAtLeast(1)
            state.captureHead(page)
            saveItems(sourceId, page, totals, state)
            totals.pages++
            finished = page.exhausted || reachedBoundary || knownPages >= 2 || (stopAtKnownItem && known > 0)
            state.advance(page, finished)
            persist(sourceId, state)
            totals.historyComplete = state.historyComplete
            totals.latestComplete = finished
            onProgress(totals.progress(maxPages, "latest"))
        }
        return totals
    }

    private fun saveItems(sourceId: Long, page: FavoriteFetchPage, totals: XLatestSyncResult, state: ScanState) {
        page.items.forEach { draft ->
            val existing = items.getBySourceExternalId(sourceId, draft.externalId)
            val positioned = draft.copy(favoritedAt = draft.favoritedAt ?: existing?.favorited_at ?: state.order--)
            val changed = if (existing != null && !shouldFetchDetail(sourceId, draft)) {
                items.markSeen(existing.id, positioned.favoritedAt)
                false
            } else items.upsertDraft(sourceId, positioned).second
            totals.seen++
            if (existing == null) totals.added++
            if (changed) totals.changed++
        }
    }

    private fun readConfig(sourceId: Long): JsonObject = runCatching {
        Json.parseToJsonElement(sources.getById(sourceId)?.config_json.orEmpty()).jsonObject
    }.getOrDefault(JsonObject(emptyMap()))

    private fun persist(sourceId: Long, state: ScanState) {
        val values = readConfig(sourceId).toMutableMap()
        state.cursor?.let { values["x_latest_cursor"] = JsonPrimitive(it) } ?: values.remove("x_latest_cursor")
        values["x_latest_boundary"] = JsonArray(state.boundary.map(::JsonPrimitive))
        values["x_latest_candidate"] = JsonArray(state.candidate.map(::JsonPrimitive))
        values["x_latest_order"] = JsonPrimitive(state.order)
        values["history_complete"] = JsonPrimitive(state.historyComplete)
        state.historyCursor?.let { values["history_cursor"] = JsonPrimitive(it) } ?: values.remove("history_cursor")
        sources.updateConfigJson(sourceId, JsonObject(values).toString())
    }

    private class ScanState(config: JsonObject) {
        var order = config["x_latest_order"]?.jsonPrimitive?.longOrNull ?: Clock.System.now().toEpochMilliseconds()
        var cursor = config["x_latest_cursor"]?.jsonPrimitive?.contentOrNull
        var boundary = config["x_latest_boundary"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        var candidate = config["x_latest_candidate"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        var historyCursor = config["history_cursor"]?.jsonPrimitive?.contentOrNull
        var historyComplete = config["history_complete"]?.jsonPrimitive?.booleanOrNull ?: false

        fun captureHead(page: FavoriteFetchPage) {
            if (cursor != null) return
            candidate = page.items.map { it.externalId }
            if (!historyComplete && historyCursor == null) historyCursor = page.nextCursor
        }

        fun advance(page: FavoriteFetchPage, finished: Boolean) {
            if (!historyComplete && cursor != null && historyCursor == cursor) historyCursor = page.nextCursor
            cursor = if (finished) null else page.nextCursor
            if (finished) boundary = candidate
            if (page.exhausted) {
                historyComplete = true
                historyCursor = null
            }
        }
    }
}

internal data class XLatestSyncResult(
    var pages: Int = 0,
    var seen: Int = 0,
    var added: Int = 0,
    var changed: Int = 0,
    var historyComplete: Boolean = false,
    var latestComplete: Boolean = false,
) {
    fun progress(maxPages: Int, phase: String) = FavoriteSyncProgress(
        phase, pages, maxPages, seen, historyComplete, added, seen - added, latestComplete,
    )
}

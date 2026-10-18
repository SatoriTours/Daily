package com.dailysatori.service.hotnews

import com.dailysatori.service.retryTransientFailure
import com.dailysatori.service.diagnostics.*
import com.dailysatori.service.unifiednews.UnifiedNewsSourceItem
import com.dailysatori.service.unifiednews.UnifiedNewsSourceType
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock

class HotNewsService(
    private val client: HotNewsClient,
    private val store: HotNewsStore,
    private val translate: suspend (List<HotNewsItem>) -> List<HotNewsItem>,
    private val loadContent: suspend (String) -> String = { "" },
) {
    private val mutableBoards = MutableStateFlow(HotNewsPlatform.entries.map { store.load(it) ?: HotNewsBoard(it) })
    val boards = mutableBoards.asStateFlow()
    private val refreshMutexes = HotNewsPlatform.entries.associateWith { Mutex() }
    private val contentMutex = Mutex()
    private val configMutex = Mutex()
    private val translationSlots = Semaphore(2)
    val preferences get() = store.preferences

    suspend fun configure(platform: HotNewsPlatform, enabled: Boolean, feedUrl: String) = configMutex.withLock {
        val previous = preferences.value.source(platform)
        store.configure(platform, enabled, feedUrl)
        val changedUrl = previous.feedUrl != preferences.value.source(platform).feedUrl
        update(platform) {
            if (changedUrl) HotNewsBoard(platform) else it.copy(refreshing = false, error = null)
        }
        if (changedUrl) store.save(board(platform))
    }

    suspend fun refresh(platform: HotNewsPlatform? = null, force: Boolean = false) {
        val platforms = (platform?.let(::listOf) ?: HotNewsPlatform.entries)
            .filter { preferences.value.source(it).enabled }
        coroutineScope {
            platforms.map { source -> async {
                refreshMutexes.getValue(source).withLock {
                    DiagnosticLog.diagnostics.operation(DiagnosticSource.AI,
                        fields = mapOf("operation" to "hot_news_refresh", "hotNewsPlatform" to source.key,
                            "aiPurpose" to "external_content"),
                        isFailure = { _: Unit -> board(source).error != null }) { refreshBoard(source, force) }
                }
            } }.awaitAll()
        }
    }

    suspend fun retryTranslations(platform: HotNewsPlatform) = DiagnosticLog.diagnostics.operation(
        DiagnosticSource.AI,
        fields = mapOf("operation" to "hot_news_retry", "hotNewsPlatform" to platform.key, "aiPurpose" to "external_content"),
        isFailure = { _: Unit -> board(platform).error != null },
    ) {
        logHotNewsProgress(platform, "retry_queued")
        refreshMutexes.getValue(platform).withLock {
            logHotNewsProgress(platform, "retry_running")
            refreshBoard(platform, force = false, translationsOnly = true)
        }
    }

    private suspend fun refreshBoard(platform: HotNewsPlatform, force: Boolean, translationsOnly: Boolean = false) {
        val config = preferences.value.source(platform)
        if (!config.enabled) return
        val previous = board(platform)
        val ttl = if (platform == HotNewsPlatform.GITHUB) 60 * 60_000L else 15 * 60_000L
        val fresh = previous.items.isNotEmpty() && now() - previous.fetchedAt in 0 until ttl
        if (!translationsOnly && !force && fresh && previous.error == null && previous.items.none(::needsTranslation)) {
            logHotNewsProgress(platform, "cache", mapOf("cacheUsed" to "true", "count" to previous.items.size.toString(), "pendingCount" to "0"))
            return
        }
        commit(platform, config) { update(platform) { it.copy(refreshing = true, error = null) } }
        try {
            val useCache = translationsOnly || (!force && fresh)
            val raw = if (useCache) previous.items else client.fetch(platform, config.feedUrl)
            val cached = board(platform).items.associateBy { it.id }
            val merged = raw.map { item ->
                cached[item.id]?.takeIf { it.fingerprint == item.fingerprint }?.let {
                    item.copy(chineseTitle = it.chineseTitle, chineseSummary = it.chineseSummary, originalContent = it.originalContent)
                } ?: item
            }
            val fetchedAt = if (useCache) previous.fetchedAt else now()
            commit(platform, config) { update(platform) { it.copy(items = merged, fetchedAt = fetchedAt) } }
            logHotNewsProgress(platform, "cache", mapOf("cacheUsed" to useCache.toString(),
                "count" to merged.size.toString(), "pendingCount" to merged.count(::needsTranslation).toString()))
            translateBoard(platform, config)
            commit(platform, config) { store.save(board(platform)) }
        } catch (e: CancellationException) { throw e }
        catch (error: Exception) {
            val failure = if (error is NewsFeedNotConfigured) HotNewsError.FEED_NOT_CONFIGURED else HotNewsError.FETCH_FAILED
            commit(platform, config) { update(platform) { it.copy(error = failure) } }
        } finally { commit(platform, config) { update(platform) { it.copy(refreshing = false) } } }
    }

    private suspend fun translateBoard(platform: HotNewsPlatform, config: HotNewsSourceConfig) {
        if (preferences.value.source(platform) != config) return
        val pending = board(platform).items.filter(::needsTranslation)
        for (batch in pending.chunked(5)) {
            if (preferences.value.source(platform) != config) return
            try {
                val remaining = try { translateBatch(platform, config, batch) }
                    catch (_: HotNewsTranslationFormatException) { batch }
                if (batch.size > 1) for (item in remaining) {
                    try { translateBatch(platform, config, listOf(item)) }
                    catch (_: HotNewsTranslationFormatException) { /* Keep other successful entries. */ }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: HotNewsAiNotConfigured) {
                commit(platform, config) { update(platform) { it.copy(error = HotNewsError.AI_NOT_CONFIGURED) } }; return
            }
            catch (error: Exception) {
                if (isHotNewsNetworkFailure(error)) {
                    commit(platform, config) { update(platform) { it.copy(error = HotNewsError.TRANSLATION_NETWORK_FAILED) } }
                    return // Do not fan a broken connection out into every item or remaining batch.
                }
            }
        }
        commit(platform, config) {
            if (board(platform).items.any(::needsTranslation)) {
                update(platform) { it.copy(error = HotNewsError.TRANSLATION_FAILED) }
            }
        }
    }

    private fun needsTranslation(item: HotNewsItem): Boolean =
        item.chineseTitle == null && (item.displayTitle == null ||
            (item.description.isNotBlank() && !isChineseHotNewsText(item.description)))

    private suspend fun translateBatch(platform: HotNewsPlatform, config: HotNewsSourceConfig,
        batch: List<HotNewsItem>): List<HotNewsItem> {
        if (preferences.value.source(platform) != config) return emptyList()
        var attempt = 0
        logHotNewsProgress(platform, "translation_batch", mapOf("count" to batch.size.toString()))
        val translated = retryTransientFailure(maxAttempts = 3, initialDelayMs = 1_000,
            shouldRetry = ::isHotNewsNetworkFailure) {
            requestTranslation(platform, config, batch, ++attempt)
        }.filter { it.platform == platform && !it.chineseTitle.isNullOrBlank() }.associateBy { it.id }
        commit(platform, config) {
            update(platform) { current -> current.copy(items = current.items.map { item ->
                translated[item.id]?.takeIf { it.fingerprint == item.fingerprint }?.let {
                    item.copy(chineseTitle = it.chineseTitle, chineseSummary = it.chineseSummary)
                } ?: item
            }) }
            store.save(board(platform))
        }
        val remaining = batch.filter { translated[it.id]?.fingerprint != it.fingerprint }
        logHotNewsProgress(platform, "translation_result", mapOf("translatedCount" to (batch.size - remaining.size).toString(),
            "pendingCount" to remaining.size.toString()))
        return remaining
    }

    private suspend fun requestTranslation(platform: HotNewsPlatform, config: HotNewsSourceConfig,
        batch: List<HotNewsItem>, attempt: Int): List<HotNewsItem> = translationSlots.withPermit {
        if (preferences.value.source(platform) != config) return@withPermit emptyList()
        logHotNewsProgress(platform, "translation_request", mapOf("attempt" to attempt.toString(), "count" to batch.size.toString()))
        try { translate(batch) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            logHotNewsProgress(platform, "translation_failure",
                mapOf("attempt" to attempt.toString(), "hotNewsFailure" to hotNewsFailureKind(error)), error)
            throw error
        }
    }

    fun find(identity: String): HotNewsItem? = boards.value.asSequence().flatMap { it.items.asSequence() }.firstOrNull { it.identity == identity }

    suspend fun ensureContent(item: HotNewsItem): HotNewsItem = contentMutex.withLock {
        val current = find(item.identity) ?: item
        if (current.originalContent.length >= 40) return@withLock current
        val content = withTimeoutOrNull(20_000) { loadContent(current.url) }?.trim()?.take(12_000).orEmpty()
        require(content.length >= 40) { "暂未获取可阅读正文" }
        val enriched = current.copy(originalContent = content)
        update(item.platform) { existing -> existing.copy(items = existing.items.map {
            if (it.id == enriched.id && it.fingerprint == enriched.fingerprint) enriched else it
        }) }
        store.save(board(item.platform))
        enriched
    }

    suspend fun summarySources(): List<UnifiedNewsSourceItem> {
        refresh()
        val configs = preferences.value
        val recent = now() - 24 * 60 * 60_000L
        val candidates = boards.value.filter { it.fetchedAt >= recent && preferences.value.source(it.platform).enabled }.flatMap { b ->
            b.items.filter { it.displayTitle != null }.take(2)
        }
        return candidates.mapNotNull { item ->
            val config = configs.source(item.platform)
            if (!config.enabled || preferences.value.source(item.platform) != config) return@mapNotNull null
            try {
                val article = ensureContent(item)
                if (preferences.value.source(item.platform) != config) return@mapNotNull null
                UnifiedNewsSourceItem("", UnifiedNewsSourceType.HOT_NEWS, sourceFilename = article.identity,
                    sourceUrl = article.url, title = article.displayTitle ?: return@mapNotNull null,
                    summary = article.displaySummary.ifBlank { article.originalContent.take(1000) },
                    sourceTime = board(item.platform).fetchedAt,
                    content = "内容来源：${item.platform.displayName}，${if (item.fromFeed) "订阅顺序" else "排名"} ${item.rank}。\n以下为来源正文：\n${article.originalContent}")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        }
    }

    private fun board(platform: HotNewsPlatform): HotNewsBoard = boards.value.first { it.platform == platform }
    private suspend fun commit(platform: HotNewsPlatform, config: HotNewsSourceConfig, action: () -> Unit) = configMutex.withLock {
        if (preferences.value.source(platform) == config) action()
    }
    private fun update(platform: HotNewsPlatform, transform: (HotNewsBoard) -> HotNewsBoard) {
        mutableBoards.update { list -> list.map { if (it.platform == platform) transform(it) else it } }
    }
    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
}

private fun isHotNewsNetworkFailure(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.take(8).any { it is IOException || it is HttpRequestTimeoutException }

private fun hotNewsFailureKind(error: Throwable): String = when {
    generateSequence(error) { it.cause }.take(8).any { it is HttpRequestTimeoutException } -> "timeout"
    isHotNewsNetworkFailure(error) -> "network"
    error is HotNewsTranslationFormatException -> "format"
    error is HotNewsAiNotConfigured -> "configuration"
    else -> "other"
}

private fun logHotNewsProgress(platform: HotNewsPlatform, stage: String, fields: Map<String, String> = emptyMap(), error: Throwable? = null) {
    DiagnosticLog.diagnostics.emit(
        if (error == null) DiagnosticCode.OPERATION_PROGRESS else DiagnosticCode.OPERATION_FAILED,
        DiagnosticSource.AI, if (error == null) DiagnosticLevel.INFO else DiagnosticLevel.WARNING,
        fields = mapOf("hotNewsPlatform" to platform.key, "hotNewsStage" to stage, "aiPurpose" to "external_content") + fields,
        error = error,
    )
}

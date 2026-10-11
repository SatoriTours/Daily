package com.dailysatori.service.hotnews

import com.dailysatori.service.unifiednews.UnifiedNewsSourceItem
import com.dailysatori.service.unifiednews.UnifiedNewsSourceType
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
    private val refreshMutex = Mutex()
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

    suspend fun refresh(platform: HotNewsPlatform? = null, force: Boolean = false) = refreshMutex.withLock {
        val platforms = (platform?.let(::listOf) ?: HotNewsPlatform.entries)
            .filter { preferences.value.source(it).enabled }
        coroutineScope { platforms.map { async { refreshBoard(it, force) } }.awaitAll() }
        Unit
    }

    private suspend fun refreshBoard(platform: HotNewsPlatform, force: Boolean) {
        val config = preferences.value.source(platform)
        if (!config.enabled) return
        val previous = board(platform)
        val ttl = if (platform == HotNewsPlatform.GITHUB) 60 * 60_000L else 15 * 60_000L
        val fresh = previous.items.isNotEmpty() && now() - previous.fetchedAt in 0 until ttl
        if (!force && fresh && previous.error == null) return
        commit(platform, config) { update(platform) { it.copy(refreshing = true, error = null) } }
        try {
            val raw = if (!force && fresh) previous.items else client.fetch(platform, config.feedUrl)
            val cached = board(platform).items.associateBy { it.id }
            val merged = raw.map { item ->
                cached[item.id]?.takeIf { it.fingerprint == item.fingerprint }?.let {
                    item.copy(chineseTitle = it.chineseTitle, chineseSummary = it.chineseSummary, originalContent = it.originalContent)
                } ?: item
            }
            val fetchedAt = if (!force && fresh) previous.fetchedAt else now()
            commit(platform, config) { update(platform) { it.copy(items = merged, fetchedAt = fetchedAt) } }
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
        val pending = board(platform).items.filter { item ->
            item.chineseTitle == null && (item.displayTitle == null ||
                (item.description.isNotBlank() && !isChineseHotNewsText(item.description)))
        }
        for (batch in pending.chunked(15)) {
            if (preferences.value.source(platform) != config) return
            try {
                val translated = translationSlots.withPermit { translate(batch) }.associateBy { it.id }
                commit(platform, config) {
                    update(platform) { current -> current.copy(items = current.items.map { item ->
                        translated[item.id]?.takeIf { it.fingerprint == item.fingerprint } ?: item
                    }) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: HotNewsAiNotConfigured) {
                commit(platform, config) { update(platform) { it.copy(error = HotNewsError.AI_NOT_CONFIGURED) } }; break
            }
            catch (_: Exception) { commit(platform, config) { update(platform) { it.copy(error = HotNewsError.TRANSLATION_FAILED) } } }
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
                    summary = article.displaySummary, sourceTime = board(item.platform).fetchedAt,
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

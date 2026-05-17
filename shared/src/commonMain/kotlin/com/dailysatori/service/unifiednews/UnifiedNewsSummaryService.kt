package com.dailysatori.service.unifiednews

import co.touchlab.kermit.Logger
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.data.repository.UnifiedNewsSummaryRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.service.remotenews.RemoteDigest
import com.dailysatori.service.remotenews.RemoteNewsResult
import com.dailysatori.service.remotenews.RemoteNewsService
import com.dailysatori.shared.db.Article
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

class UnifiedNewsSummaryService(
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
    private val articleRepo: ArticleRepository,
    private val remoteNewsService: RemoteNewsService,
    private val remoteNewsSourceRepo: RemoteNewsSourceRepository,
    private val summaryRepo: UnifiedNewsSummaryRepository,
) {
    private val log = Logger.withTag("UnifiedNewsSummary")

    suspend fun generate(
        window: UnifiedNewsWindow,
        force: Boolean = false,
        ignoreSourceTimeFilter: Boolean = false,
    ): UnifiedNewsGenerationResult {
        if (!force && hasSuccessfulSummary(window)) {
            return UnifiedNewsGenerationResult(true, UnifiedNewsSummaryStatus.SUCCESS)
        }

        val warnings = mutableListOf<String>()
        val sources = try {
            budgetUnifiedNewsSources(collectSources(window, warnings, ignoreSourceTimeFilter))
        } catch (e: Exception) {
            log.w(e) { "Unified news source collection failed" }
            return saveFailure(window, emptyList(), warnings, "新闻来源收集失败，请稍后重试")
        }
        if (sources.isEmpty()) return persistEmpty(window, warnings)

        val config = aiConfigService.getDefaultConfig()
        if (config == null) return saveFailure(window, sources, warnings, "请先配置默认 AI 服务")

        val content = try {
            aiService.summarize(
                content = buildUnifiedNewsPrompt(window, sources),
                systemPrompt = "你是 Daily Satori 的新闻编辑，请输出可靠、克制且带引用的中文 Markdown 汇总。",
                apiAddress = config.api_address,
                apiToken = config.api_token,
                modelName = config.model_name,
                provider = config.provider,
            )
        } catch (e: Exception) {
            log.w(e) { "Unified news AI generation failed" }
            return saveFailure(window, sources, warnings, aiGenerationFailureMessage(e))
        }

        val invalid = invalidCitationTokens(content, sources)
        if (invalid.isNotEmpty()) {
            return saveFailure(window, sources, warnings, "AI 返回了无效引用：${invalid.joinToString()}")
        }
        return persistSuccess(window, sources, warnings, content)
    }

    suspend fun generateDaily(
        force: Boolean = false,
        ignoreSourceTimeFilter: Boolean = false,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): UnifiedNewsGenerationResult {
        val window = dailyUnifiedNewsWindowFor(now, timeZone)
        require(window.key == UnifiedNewsWindowKey.DAILY)
        return generate(
            window = window,
            force = force,
            ignoreSourceTimeFilter = ignoreSourceTimeFilter,
        )
    }

    private fun hasSuccessfulSummary(window: UnifiedNewsWindow): Boolean =
        summaryRepo.getByWindow(window.summaryDate, window.key.value)?.status == UnifiedNewsSummaryStatus.SUCCESS.value

    private suspend fun collectSources(
        window: UnifiedNewsWindow,
        warnings: MutableList<String>,
        ignoreSourceTimeFilter: Boolean,
    ): List<UnifiedNewsSourceItem> {
        val sources = mutableListOf<UnifiedNewsSourceItem>()
        val refCounts = mutableMapOf<String, Int>()
        addSources(sources, refCounts, collectConfiguredRemoteArticles(window, warnings, ignoreSourceTimeFilter))
        addSources(sources, refCounts, collectLocalFavorites(window))
        return sources
    }

    private fun collectLocalFavorites(window: UnifiedNewsWindow): List<UnifiedNewsSourceItem> =
        articleRepo.getFavoritesByDateRangeSync(window.startMs, window.endMs).map { it.toUnifiedSource() }

    private suspend fun collectConfiguredRemoteArticles(
        window: UnifiedNewsWindow,
        warnings: MutableList<String>,
        ignoreSourceTimeFilter: Boolean,
    ): List<UnifiedNewsSourceItem> {
        val articles = mutableListOf<UnifiedNewsSourceItem>()
        remoteNewsSourceRepo.getEnabled().forEach { source ->
            when (val config = remoteNewsService.configOrFailure(source.base_url, source.api_token)) {
                is RemoteNewsResult.Success -> when (val result = remoteNewsService.fetchTopArticlesToday(config.value, page = 1, limit = 50)) {
                    is RemoteNewsResult.Success -> {
                        articles += result.value.articles.mapNotNull { article ->
                            article.toUnifiedSource(
                                window = window,
                                ignoreSourceTimeFilter = ignoreSourceTimeFilter,
                                sourceFilename = remoteNewsSourceRouteKey(source.id),
                            )
                        }
                    }
                    is RemoteNewsResult.Failure -> warnRemoteSourceFailure(warnings, source.name, result.message)
                }
                is RemoteNewsResult.Failure -> warnRemoteSourceFailure(warnings, source.name, config.message)
            }
        }
        return deduplicateUnifiedNewsSources(articles)
    }

    private fun addSources(
        target: MutableList<UnifiedNewsSourceItem>,
        refCounts: MutableMap<String, Int>,
        sources: List<UnifiedNewsSourceItem>,
    ) {
        sources.forEach { source ->
            val prefix = source.sourceType.prefix
            val next = (refCounts[prefix] ?: 0) + 1
            refCounts[prefix] = next
            target += source.copy(refKey = "$prefix$next")
        }
    }

    private fun persistEmpty(window: UnifiedNewsWindow, warnings: List<String>): UnifiedNewsGenerationResult {
        val message = "当前时间窗口暂无可总结新闻，skip the AI call"
        if (preserveExistingContent(window, "暂无可总结新闻", UnifiedNewsSummaryStatus.EMPTY.value, message, warnings)) {
            return UnifiedNewsGenerationResult(true, UnifiedNewsSummaryStatus.EMPTY, message)
        }
        summaryRepo.saveSummaryWithSources(
            window = window,
            title = "暂无可总结新闻",
            content = "",
            status = UnifiedNewsSummaryStatus.EMPTY.value,
            errorMessage = message,
            sourceWarnings = warnings.distinct().joinToString("\n").ifBlank { null },
            generatedAt = Clock.System.now().toEpochMilliseconds(),
            sources = emptyList(),
        )
        return UnifiedNewsGenerationResult(true, UnifiedNewsSummaryStatus.EMPTY, message)
    }

    private fun saveFailure(
        window: UnifiedNewsWindow,
        sources: List<UnifiedNewsSourceItem>,
        warnings: List<String>,
        message: String,
    ): UnifiedNewsGenerationResult {
        if (preserveExistingContent(window, "统一新闻总结生成失败", UnifiedNewsSummaryStatus.FAILED.value, message, warnings)) {
            return UnifiedNewsGenerationResult(false, UnifiedNewsSummaryStatus.FAILED, message)
        }
        summaryRepo.saveSummaryWithSources(
            window = window,
            title = "统一新闻总结生成失败",
            content = "",
            status = UnifiedNewsSummaryStatus.FAILED.value,
            errorMessage = message,
            sourceWarnings = warnings.distinct().joinToString("\n").ifBlank { null },
            generatedAt = Clock.System.now().toEpochMilliseconds(),
            sources = sources,
        )
        return UnifiedNewsGenerationResult(false, UnifiedNewsSummaryStatus.FAILED, message)
    }

    private fun preserveExistingContent(
        window: UnifiedNewsWindow,
        title: String,
        status: String,
        message: String,
        warnings: List<String>,
    ): Boolean {
        val existing = summaryRepo.getByWindow(window.summaryDate, window.key.value)
        if (existing?.content.isNullOrBlank()) return false
        summaryRepo.upsertSummary(
            window = window,
            title = title,
            content = existing.content,
            status = status,
            errorMessage = message,
            sourceWarnings = warnings.distinct().joinToString("\n").ifBlank { null },
            generatedAt = Clock.System.now().toEpochMilliseconds(),
        )
        return true
    }

    private fun persistSuccess(
        window: UnifiedNewsWindow,
        sources: List<UnifiedNewsSourceItem>,
        warnings: List<String>,
        content: String,
    ): UnifiedNewsGenerationResult {
        summaryRepo.saveSummaryWithSources(
            window = window,
            title = "今日统一新闻总结",
            content = content,
            status = UnifiedNewsSummaryStatus.SUCCESS.value,
            errorMessage = null,
            sourceWarnings = warnings.distinct().joinToString("\n").ifBlank { null },
            generatedAt = Clock.System.now().toEpochMilliseconds(),
            sources = sources,
        )
        return UnifiedNewsGenerationResult(true, UnifiedNewsSummaryStatus.SUCCESS)
    }

    private fun warnRemoteSourceFailure(warnings: MutableList<String>, sourceName: String, message: String) =
        warnSourceFailure(warnings, "$sourceName: $message")

    private fun warnSourceFailure(warnings: MutableList<String>, message: String) {
        log.w { message }
        warnings += message
    }

    private fun aiGenerationFailureMessage(error: Exception): String {
        val detail = error.message.orEmpty()
        return when {
            detail.contains("Timed out", ignoreCase = true) -> "AI 总结生成超时，请稍后手动重试"
            detail.contains("JsonParseException", ignoreCase = true) -> "AI 服务返回了非 JSON 响应，请检查 AI 地址或模型配置"
            detail.isNotBlank() -> "AI 总结生成失败：${detail.take(120)}"
            else -> "AI 总结生成失败，请稍后重试"
        }
    }
}

fun parseSourceTimeMillis(value: String): Long? = try {
    Instant.parse(value).toEpochMilliseconds()
} catch (_: Exception) {
    null
}

fun budgetUnifiedNewsSources(
    sources: List<UnifiedNewsSourceItem>,
    maxSources: Int = 30,
    maxContentChars: Int = 8000,
): List<UnifiedNewsSourceItem> = sources
    .take(maxSources)
    .map { source -> source.copy(content = source.content.take(maxContentChars)) }

fun remoteDigestArticlesToUnifiedSources(
    digests: List<RemoteDigest>,
    window: UnifiedNewsWindow,
    ignoreSourceTimeFilter: Boolean,
): List<UnifiedNewsSourceItem> {
    val articles = digests.flatMap { digest ->
        val digestTime = digest.generatedAt ?: digest.date
        digest.articles.mapNotNull { it.toUnifiedSource(window, ignoreSourceTimeFilter, digestTime) }
    }
    return deduplicateUnifiedNewsSources(articles)
}

fun deduplicateUnifiedNewsSources(items: List<UnifiedNewsSourceItem>): List<UnifiedNewsSourceItem> =
    items.distinctBy { listOf(it.sourceId, it.sourceUrl, it.title) }

fun remoteNewsSourceRouteKey(id: Long): String = "remote_news_source:$id"

private fun Article.toUnifiedSource(): UnifiedNewsSourceItem = UnifiedNewsSourceItem(
    refKey = "",
    sourceType = UnifiedNewsSourceType.LOCAL_FAVORITE,
    sourceId = id,
    sourceUrl = url,
    title = ai_title ?: title ?: url ?: "本地收藏 $id",
    summary = ai_content ?: ai_markdown_content?.take(500) ?: title ?: url ?: "收藏文章",
    sourceTime = created_at,
    content = ai_markdown_content ?: ai_content ?: title ?: url.orEmpty(),
)

private fun RemoteArticle.toUnifiedSource(
    window: UnifiedNewsWindow,
    ignoreSourceTimeFilter: Boolean,
    fallbackTime: String? = null,
    sourceFilename: String? = null,
): UnifiedNewsSourceItem? {
    val rawTime = processedAt ?: createdAt ?: fallbackTime
    val time = rawTime?.let(::parseSourceTimeMillis) ?: parseDigestDateMillis(fallbackTime) ?: return null
    if (!ignoreSourceTimeFilter && time !in window.startMs..window.endMs) return null
    return UnifiedNewsSourceItem(
        refKey = "",
        sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE,
        sourceId = id,
        sourceFilename = sourceFilename,
        sourceUrl = url,
        title = title ?: url ?: "远程文章 $id",
        summary = summary ?: viewpoints.joinToString("；").ifBlank { title ?: url ?: "远程文章" },
        sourceTime = time,
        content = content ?: summary ?: viewpoints.joinToString("\n").ifBlank { title.orEmpty() },
    )
}

private fun RemoteArticle.toUnifiedSourceFromDigest(fallback: UnifiedNewsSourceItem): UnifiedNewsSourceItem = UnifiedNewsSourceItem(
    refKey = fallback.refKey,
    sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE,
    sourceId = id,
    sourceUrl = url ?: fallback.sourceUrl,
    title = title ?: fallback.title,
    summary = summary ?: viewpoints.joinToString("；").ifBlank { fallback.summary },
    sourceTime = fallback.sourceTime,
    content = content ?: summary ?: viewpoints.joinToString("\n").ifBlank { fallback.content },
)

private fun parseDigestDateMillis(value: String?): Long? {
    val date = value?.takeIf { it.length == 10 } ?: return null
    return try {
        LocalDate.parse(date).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    } catch (_: Exception) {
        null
    }
}

fun crayfishArticleRouteKey(category: String, date: String, articleId: String): String = "$category/$date/articles/$articleId"

package com.dailysatori.service.unifiednews

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnifiedNewsHotSourceBudgetTest {
    @Test fun dailyBudgetKeepsEveryHotPlatformAlongsideNewerNewsAndFavorites() {
        val platforms = listOf("github", "hackernews", "x", "xiaohongshu", "toutiao", "zhihu", "v2ex", "linuxdo")
        val otherSources = listOf(UnifiedNewsSourceType.REMOTE_ARTICLE, UnifiedNewsSourceType.LOCAL_FAVORITE)
            .flatMap { type -> (1..18).map { item(type, "$type-$it", 10_000L + it) } }
        val hotSources = platforms.flatMapIndexed { index, platform ->
            (1..2).map { rank -> item(UnifiedNewsSourceType.HOT_NEWS, "$platform:$rank", index * 10L + rank) }
        }
        val selected = prepareUnifiedNewsSources(otherSources + hotSources)
        assertEquals(30, selected.size)
        assertEquals(platforms.toSet(), selected.filter { it.sourceType == UnifiedNewsSourceType.HOT_NEWS }
            .map { it.sourceFilename!!.substringBefore(':') }.toSet())
        assertTrue(selected.any { it.sourceType == UnifiedNewsSourceType.REMOTE_ARTICLE })
        assertTrue(selected.any { it.sourceType == UnifiedNewsSourceType.LOCAL_FAVORITE })
    }

    @Test fun hotPlatformReservationRespectsTotalAndTypeLimitsAndRemainsDeduplicated() {
        val sources = (1..5).flatMap { platform -> (1..2).map { rank ->
            item(UnifiedNewsSourceType.HOT_NEWS, "platform$platform:$rank", platform * 10L + rank)
        } }
        val selected = prepareUnifiedNewsSources(sources, maxSources = 4, maxPerSourceType = 3)
        assertEquals(3, selected.size)
        assertEquals(3, selected.map { it.sourceFilename!!.substringBefore(':') }.distinct().size)
        assertEquals(3, selected.map { it.refKey }.distinct().size)
        assertTrue(prepareUnifiedNewsSources(sources, maxSources = 0).isEmpty())
    }

    private fun item(type: UnifiedNewsSourceType, id: String, time: Long) = UnifiedNewsSourceItem(
        refKey = id,
        sourceType = type,
        sourceFilename = id,
        sourceUrl = "https://example.com/$id",
        title = "来源 $id 的新闻",
        summary = "",
        content = "这是 $id 的可阅读来源正文，包含完整的事件信息和不同来源的具体背景。".repeat(3),
        sourceTime = time,
    )
}

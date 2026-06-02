package com.dailysatori.ui.feature.unifiednews

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class UnifiedNewsSourceTabsStyleTest {
    @Test
    fun sourceTabsUsePrimaryBlueSelectedAccent() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsSourceSwitcher.kt").readText()
        val tabsBody = source.extractCallBlock("internal fun UnifiedNewsSourceTabs(")

        assertTrue(tabsBody.contains("FilterChipDefaults.filterChipColors"))
        assertTrue(tabsBody.contains("selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)"))
        assertTrue(tabsBody.contains("selectedLabelColor = MaterialTheme.colorScheme.primary"))
        assertTrue(tabsBody.contains("selectedLeadingIconColor = MaterialTheme.colorScheme.primary"))
    }

    @Test
    fun refreshActionUsesSubduedSurfaceVariantTone() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsSourceSwitcher.kt").readText()
        val switcherBody = source.extractCallBlock("internal fun UnifiedNewsSourceSwitcher(")

        assertTrue(switcherBody.contains("tint = MaterialTheme.colorScheme.onSurfaceVariant"))
    }

    @Test
    fun unifiedNewsListsUseStableKeysAndSharedPadding() {
        val summary = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsSummaryContent.kt").readText()
        val remote = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsRemoteSourceContent.kt").readText()

        assertTrue(summary.contains("contentPadding = newsCompactListContentPadding()"))
        assertTrue(summary.contains("items(visibleSummaries, key = { it.id })"))
        assertTrue(remote.contains("contentPadding = newsCompactListContentPadding()"))
        assertTrue(remote.contains("items(articles, key = { it.id })"))
    }

    private fun String.extractCallBlock(anchor: String): String {
        assertTrue(contains(anchor), "Missing call anchor: $anchor")
        val start = indexOf(anchor)
        val signatureEnd = indexOf(") {", start)
        val bodyStart = if (signatureEnd >= 0) indexOf('{', signatureEnd) else -1
        assertTrue(bodyStart >= 0, "Missing block body for call anchor: $anchor")

        var depth = 0
        for (index in bodyStart until length) {
            when (this[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return substring(start, index + 1)
                }
            }
        }
        throw AssertionError("Missing closing brace for call anchor: $anchor")
    }
}

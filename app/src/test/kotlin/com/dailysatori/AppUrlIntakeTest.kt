package com.dailysatori

import kotlin.test.Test
import kotlin.test.assertEquals

class AppUrlIntakeTest {
    @Test
    fun extractsFirstHttpUrlFromSharedText() {
        assertEquals(
            "https://example.com/a?b=1",
            extractFirstUrl("看看这个链接 https://example.com/a?b=1 很有意思"),
        )
    }

    @Test
    fun normalizesUrlForDuplicateComparison() {
        assertEquals("https://example.com/a", normalizeArticleUrl(" https://example.com/a/ "))
        assertEquals("http://example.com/a", normalizeArticleUrl("http://example.com/a"))
    }

    @Test
    fun detectsExistingUrlAfterNormalization() {
        assertEquals(
            true,
            articleUrlExists("https://example.com/a/", listOf("https://example.com/a")),
        )
        assertEquals(
            false,
            articleUrlExists("https://example.com/b", listOf("https://example.com/a")),
        )
    }

    @Test
    fun suppressesRepeatedClipboardUrlAfterUserDecision() {
        val state = ClipboardUrlPromptState()

        assertEquals(true, state.shouldPrompt("https://example.com/a"))
        state.markHandled("https://example.com/a")
        assertEquals(false, state.shouldPrompt("https://example.com/a/"))
        assertEquals(true, state.shouldPrompt("https://example.com/b"))
    }

    @Test
    fun skipsClipboardCheckWhenLaunchWasTriggeredByShareText() {
        assertEquals(false, shouldCheckClipboardOnForeground(launchedFromShare = true))
        assertEquals(true, shouldCheckClipboardOnForeground(launchedFromShare = false))
    }

    @Test
    fun suppressesNextClipboardCheckAfterShareHandling() {
        val state = ClipboardCheckGate()

        assertEquals(true, state.shouldCheck())
        state.suppressNextCheck()
        assertEquals(false, state.shouldCheck())
        assertEquals(true, state.shouldCheck())
    }

    @Test
    fun visibleMessagesExplainClipboardPromptAndDuplicateSnackbar() {
        assertEquals("链接已存在", duplicateUrlSnackbarMessage())
        assertEquals("检测到剪切板链接", clipboardPromptTitle())
    }

    @Test
    fun shareReceiverMessagesUseToastText() {
        assertEquals("已开始保存文章", shareSaveStartedToastMessage())
        assertEquals("未找到链接", shareInvalidUrlToastMessage())
    }

    @Test
    fun scrollsToTopOnlyAfterArticleWasAdded() {
        assertEquals(false, shouldScrollToTopAfterArticleAdded(0))
        assertEquals(true, shouldScrollToTopAfterArticleAdded(1))
    }

    @Test
    fun countsNewLeadingArticlesAboveRememberedItem() {
        assertEquals(0, countNewLeadingArticles(listOf(10, 9, 8), rememberedTopArticleId = 10))
        assertEquals(2, countNewLeadingArticles(listOf(12, 11, 10, 9), rememberedTopArticleId = 10))
        assertEquals(0, countNewLeadingArticles(listOf(12, 11, 10, 9), rememberedTopArticleId = null))
        assertEquals(0, countNewLeadingArticles(listOf(12, 11, 10, 9), rememberedTopArticleId = 7))
    }

    @Test
    fun showsNewArticleIndicatorOnlyWhenUserIsAwayFromTop() {
        assertEquals(false, shouldShowNewArticlesIndicator(newCount = 0, isAtTop = false))
        assertEquals(false, shouldShowNewArticlesIndicator(newCount = 2, isAtTop = true))
        assertEquals(true, shouldShowNewArticlesIndicator(newCount = 2, isAtTop = false))
    }
}

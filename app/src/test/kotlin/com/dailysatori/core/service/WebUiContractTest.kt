package com.dailysatori.core.service

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebUiContractTest {
    @Test
    fun webHomeUsesTheAppTodayExperienceInsteadOfAStatisticsDashboard() {
        val html = File("src/main/assets/website/admin.html").readText()
        val script = File("src/main/assets/website/js/app.js").readText()

        assertTrue(html.contains("DAILY BRIEFING"))
        assertTrue(html.contains("更新今日汇总"))
        assertTrue(script.contains("/news/summary"))
        assertFalse(html.contains("内容趋势"))
        assertFalse(html.contains("chart.umd.min.js"))
    }

    @Test
    fun webClientDoesNotPersistTheAccessToken() {
        val script = File("src/main/assets/website/js/app.js").readText()

        assertFalse(script.contains("localStorage.setItem('ds_token'"))
        assertFalse(script.contains("'Authorization' = 'Bearer '"))
        assertTrue(script.contains("credentials: 'same-origin'"))
    }

    @Test
    fun articleDetailExposesDesktopActions() {
        val html = File("src/main/assets/website/admin.html").readText()
        val script = File("src/main/assets/website/js/app.js").readText()

        assertTrue(html.contains("toggleArticleFavorite"))
        assertTrue(html.contains("reprocessArticle"))
        assertTrue(html.contains("originalMarkdownContent"))
        assertTrue(script.contains("referrerpolicy=\"no-referrer\""))
    }

    @Test
    fun diaryAndReadingPagesExposeAppLevelEditingActions() {
        val html = File("src/main/assets/website/admin.html").readText()
        val script = File("src/main/assets/website/js/app.js").readText()

        assertTrue(html.contains("v-model=\"diaryMood\""))
        assertTrue(html.contains("selectedDiary.attachments"))
        assertTrue(html.contains("openBookEditor(currentBook)"))
        assertTrue(html.contains("openViewpointEditor"))
        assertTrue(script.contains("/books/search?q="))
        assertTrue(script.contains("/books/viewpoints/"))
        assertTrue(html.contains("collection-mode"))
        assertTrue(html.contains("reader-scroll"))
        assertTrue(html.contains("selectedDiary"))
        assertTrue(html.contains("book-hero"))
        assertTrue(html.contains("collectionItems"))
        assertTrue(html.contains("记下感悟"))
        assertTrue(script.contains("diaryExcerpt"))
        assertTrue(script.contains("closeBookDetail"))
    }

    @Test
    fun aiAssistantSupportsSessionsKnowledgeReferencesAndDesktopInput() {
        val html = File("src/main/assets/website/admin.html").readText()
        val script = File("src/main/assets/website/js/app.js").readText()

        assertTrue(html.contains("KNOWLEDGE ASSISTANT"))
        assertTrue(html.contains("message.references"))
        assertTrue(html.contains("@keydown.enter.exact.prevent=\"sendAiMessage\""))
        assertTrue(script.contains("/ai/sessions"))
        assertTrue(script.contains("/ai/chat"))
        assertTrue(script.contains("newAiSession"))
    }

    @Test
    fun todayStaysFocusedOnTheBriefingWhileTasksRemainAvailable() {
        val html = File("src/main/assets/website/admin.html").readText()
        val script = File("src/main/assets/website/js/app.js").readText()

        assertFalse(html.contains("news-source-tabs"))
        assertFalse(html.contains("sourceArticles"))
        assertFalse(html.contains("<h3>新闻来源</h3>"))
        assertTrue(html.contains("BACKGROUND WORK"))
        assertFalse(script.contains("/news/sources/"))
        assertTrue(script.contains("/tasks?showTerminal="))
        assertTrue(script.contains("cancelTask"))
    }

    @Test
    fun diaryEditorIsALargeProtectedDesktopWritingWorkspace() {
        val html = File("src/main/assets/website/admin.html").readText()
        val css = File("src/main/assets/website/css/workspace.css").readText()
        val script = File("src/main/assets/website/js/app.js").readText()

        assertTrue(html.contains("editor-shell"))
        assertTrue(html.contains("format-bar"))
        assertTrue(html.contains("diary-live-preview"))
        assertTrue(html.contains("diaryCharacterCount"))
        assertTrue(html.contains("diaryEditorDate"))
        assertTrue(css.contains("minmax(0,1fr)"))
        assertTrue(css.contains("height:100dvh"))
        assertTrue(css.contains("background:var(--surface)"))
        assertTrue(script.contains("scheduleDiaryDraft"))
        assertTrue(script.contains("restoreDiaryDraft"))
        assertTrue(script.contains("applyDiaryPrompt"))
        assertTrue(script.contains("diaryHasUnsavedChanges"))
        assertTrue(script.contains("e.key.toLowerCase()==='s'"))
    }
}

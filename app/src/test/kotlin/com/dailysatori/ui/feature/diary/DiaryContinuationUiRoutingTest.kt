package com.dailysatori.ui.feature.diary

import com.dailysatori.service.diary.DiaryThreadOverview
import com.dailysatori.service.diary.DiaryThreadSummary
import com.dailysatori.service.diary.DiaryThreadSummaryStatus
import com.dailysatori.shared.db.Diary
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiaryContinuationUiRoutingTest {

    @Test
    fun continuationEditorDoesNotPreloadSummaryAsContent() {
        // When opening continuation editor for a diary thread with an AI summary,
        // the editor content must NOT be initialized to summary text.
        val summary = DiaryThreadSummary(
            text = "AI 汇总故事总结",
            sourceRevision = 2L,
            summaryRevision = 2L,
            status = DiaryThreadSummaryStatus.ready,
            errorMessage = null,
            generatedAt = 1000L,
        )

        // Continuation mode takes continuationRootId, and existingDiary is null (or the newly created reply),
        // never initialized with summary text.
        val editorInitialText = resolveContinuationEditorInitialContent(
            continuationRootId = 1L,
            existingReply = null,
            summary = summary,
        )

        assertEquals("", editorInitialText, "续写编辑器必须空白，绝不能将 AI 汇总带入作为正文")
    }

    @Test
    fun continuationModeHidesTagEditorToAvoidOverwritingRootTags() {
        val showTagsForNormalDiary = shouldShowTagsInEditor(continuationRootId = null)
        val showTagsForContinuation = shouldShowTagsInEditor(continuationRootId = 100L)

        assertTrue(showTagsForNormalDiary, "普通日记编辑必须显示标签")
        assertFalse(showTagsForContinuation, "续写模式必须隐藏标签编辑以避免覆盖清空主日记标签")
    }

    @Test
    fun continuationEditorDateTextDistinguishesContinuationFromOriginal() {
        val rootDateText = diaryEditorHeaderTitle(existingDiary = null, continuationRootId = null)
        val editDateText = diaryEditorHeaderTitle(
            existingDiary = Diary(
                id = 1L,
                content = "原日记",
                tags = null,
                mood = null,
                images = null,
                created_at = 1767225600000L,
                updated_at = 1767225600000L,
                parent_diary_id = null,
            ),
            continuationRootId = null,
        )
        val continuationText = diaryEditorHeaderTitle(
            existingDiary = null,
            continuationRootId = 1L,
            locale = java.util.Locale.CHINA,
        )

        assertEquals("续写日记", continuationText)
        assertTrue(rootDateText.isNotEmpty() && rootDateText != "续写日记")
        assertTrue(editDateText.isNotEmpty() && editDateText != "续写日记")
    }

    @Test
    fun editOriginalVsContinueRouteDistinctInSource() {
        val file = File("app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryThreadSheet.kt").takeIf { it.exists() }
            ?: File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryThreadSheet.kt")
        val threadSheetSource = file.readText()

        assertTrue(threadSheetSource.contains("onEditOriginal: () -> Unit"))
        assertTrue(threadSheetSource.contains("onContinue: () -> Unit"))
        assertTrue(threadSheetSource.contains("onRetrySummary: () -> Unit"))
    }
}

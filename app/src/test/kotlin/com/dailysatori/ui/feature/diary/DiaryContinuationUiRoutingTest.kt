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
    fun editOriginalVsContinueRouteDistinct() {
        val root = Diary(
            id = 42L,
            content = "主日记",
            tags = "测试",
            mood = null,
            images = null,
            created_at = 1000L,
            updated_at = 1000L,
            parent_diary_id = null,
        )

        // Route for "编辑原文":
        val editOriginalTarget = resolveDiaryEditorRouteTarget(root)
        assertEquals(42L, editOriginalTarget.editingDiary?.id)
        assertNull(editOriginalTarget.continuationRootId)
        assertFalse(editOriginalTarget.isContinuation)

        // Route for "继续写":
        val continueTarget = resolveDiaryEditorRouteTarget(
            Diary(
                id = 99L,
                content = "续写",
                tags = null,
                mood = null,
                images = null,
                created_at = 2000L,
                updated_at = 2000L,
                parent_diary_id = root.id,
            ),
        )
        assertEquals(expected = 42L, actual = continueTarget.continuationRootId, message = "继续写路由的目标根 ID 必须是主日记 ID")
        assertEquals(expected = 99L, actual = continueTarget.continuationReplyId)
        assertTrue(continueTarget.isContinuation)
    }

    @Test
    fun childRecordingRestorationResolvesToParentRootId() {
        // F2: 录音控制器恢复 child 时，必须通过真实记录将 parent_diary_id 作为 continuationRootId
        val childRecordingDiary = Diary(
            id = 303L,
            content = "语音转写中...",
            tags = null,
            mood = null,
            images = null,
            created_at = 3000L,
            updated_at = 3000L,
            parent_diary_id = 101L,
        )

        val target = resolveDiaryEditorRouteTarget(childRecordingDiary)
        assertEquals(expected = 101L, actual = target.continuationRootId, message = "续写录音恢复时根 ID 必须为 parent_diary_id 而不是 child id")
        assertEquals(expected = 303L, actual = target.continuationReplyId, message = "续写录音恢复时 replyId 必须为 child id")
        assertTrue(target.isContinuation)
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

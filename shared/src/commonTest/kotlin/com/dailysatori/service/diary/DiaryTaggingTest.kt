package com.dailysatori.service.diary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryTagRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.channels.Channel
import kotlin.test.*

class DiaryTaggingTest {
    @Test
    fun editorCanonicalizationKeepsManualOwnershipWhenSynonymsCollide() {
        val draft = DiaryTagDraft.from("职业抉择,职业选择", DiaryTagState(automatic = listOf("职业抉择")), "正文", DiaryTagVocabulary())
        assertEquals(listOf("职业选择"), draft.tags)
        assertTrue(draft.automatic.isEmpty())
    }
    @Test
    fun regeneratingDraftDoesNotTreatOldAutomaticTagsAsUserRejections() = withDiary { tags, _, id ->
        tags.apply(assertNotNull(tags.prepare(id)), listOf("读书"))
        val draft = DiaryTagDraft(listOf("读书"), listOf("读书"), contentFingerprint = tags.state(id).contentFingerprint)
            .withGenerated("今天思考职业选择，也读书和锻炼。", listOf("旅行"))
        tags.saveEditedDiary(id, "今天思考职业选择，也读书和锻炼。", draft.tags, null, null, draft)
        assertTrue(tags.state(id).suppressed.isEmpty())
        tags.apply(assertNotNull(tags.prepare(id, force = true)), listOf("读书"))
        assertEquals(listOf("读书"), tags.tags(id))
    }

    @Test
    fun untouchedDraftPreservesTagsGeneratedWhileEditorWasOpen() = withDiary { tags, _, id ->
        val draft = DiaryTagDraft(emptyList())
        tags.apply(assertNotNull(tags.prepare(id)), listOf("读书"))
        tags.saveEditedDiary(id, "今天思考职业选择，也读书和锻炼。", null, null, null, draft)
        assertEquals(listOf("读书"), tags.tags(id))
        assertEquals(listOf("读书"), tags.state(id).automatic)
    }

    @Test
    fun aliasOnlyMergeRefreshesAnAlreadyOpenSearch() = withDiary { tags, diaries, id ->
        tags.edit(id, listOf("焦虑"))
        runBlocking {
            val results = Channel<List<com.dailysatori.shared.db.Diary>>(Channel.UNLIMITED)
            val job = launch { diaries.search("压力").collect { results.send(it) } }
            try {
                assertTrue(withTimeout(2_000) { results.receive() }.isEmpty())
                tags.merge("压力", "焦虑")
                withTimeout(2_000) { while (results.receive().isEmpty()) { /* wait for alias notification */ } }
            } finally { job.cancel(); results.close() }
        }
    }
    @Test
    fun addingManualTagsReducesAutomaticSlotsWithoutRemovingManualTags() {
        val draft = DiaryTagDraft(listOf("读书", "旅行", "健身"), listOf("读书", "旅行", "健身"))
            .add("家庭").add("工作").add("健康").add("园艺")
        assertEquals(listOf("家庭", "工作", "健康", "园艺"), draft.tags)
        assertTrue(draft.automatic.isEmpty())
    }

    @Test
    fun replacingTagKeepsItsPositionAndPinsTheReplacement() {
        val draft = DiaryTagDraft(listOf("读书", "旅行"), listOf("读书", "旅行")).add("学习", "读书")
        assertEquals(listOf("学习", "旅行"), draft.tags)
        assertEquals(listOf("旅行"), draft.automatic)
    }

    @Test
    fun legacyManualTagsRemainVisibleAndAreNotLostDuringEditing() = withDiary { tags, diaries, id ->
        val legacy = "这是一个用户早年写下的超过二十四个字的自定义日记标签名称"
        diaries.update(id, "正文", legacy, null, null)
        assertEquals(listOf(legacy), tags.tags(id))
        tags.edit(id, listOf(legacy, "读书"))
        assertEquals(listOf(legacy, "读书"), tags.tags(id))
    }

    @Test
    fun provenanceAndSuppressionSurviveReopeningAndResetOnlyAfterBodyChanges() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val diaries = DiaryRepository(db, driver)
            val id = runBlocking { diaries.create("正文") }
            val tags = DiaryTagRepository(db, com.dailysatori.data.repository.DiaryThreadRepository(db, driver))
            tags.apply(assertNotNull(tags.prepare(id)), listOf("读书"))
            tags.edit(id, emptyList())
            val reopened = DiaryTagRepository(DailySatoriDatabase(driver), com.dailysatori.data.repository.DiaryThreadRepository(DailySatoriDatabase(driver), driver))
            assertEquals(listOf("读书"), reopened.state(id).suppressed)
            assertNull(reopened.prepare(id))
            diaries.update(id, "新的正文", null, null, null)
            assertTrue(reopened.state(id).suppressed.isEmpty())
            assertNotNull(reopened.prepare(id))
        } finally { driver.close() }
    }
    @Test
    fun synonymReviewOnlyUsesKnownNamesAndNeverMutatesDiary() = runBlocking {
        val generator = DiaryTagGenerator { prompt, _ ->
            assertFalse(prompt.contains("private diary body"))
            """{"merges":[{"from":"感想","to":"感悟"},{"from":"读书","to":"未知"}]}"""
        }
        assertEquals(listOf(DiaryTagMerge("感想", "感悟")), generator.suggestMerges(listOf("感想", "感悟", "读书")))
    }
    @Test
    fun draftSupportsReplacePinRemoveAndRespectsManualSlots() {
        val draft = DiaryTagDraft(listOf("家庭", "读书"), listOf("读书"), contentFingerprint = diaryTagFingerprint("正文"))
        val edited = draft.remove("读书").add("旅行").pin("旅行")
        val generated = edited.withGenerated("正文", listOf("读书", "健身", "工作"))
        assertEquals(listOf("旅行", "家庭", "健身"), generated.tags)
        assertEquals(listOf("健身"), generated.automatic)
        assertEquals(listOf("读书"), generated.suppressed)
        assertEquals(listOf("旅行", "家庭"), edited.withGenerated("修改正文", listOf("读书")).tags)
    }

    @Test
    fun baselineSkipsHistoryButDetectsNewAndModifiedDiaries() = withDiary { tags, diaries, id ->
        assertTrue(tags.changedDiaryIds().isEmpty())
        diaries.update(id, "新的正文", null, null, null)
        assertEquals(listOf(id), tags.changedDiaryIds())
        assertTrue(tags.changedDiaryIds().isEmpty())
        val next = runBlocking { diaries.create("新日记") }
        assertEquals(listOf(next), tags.changedDiaryIds())
    }

    @Test
    fun mergeKeepsManualProvenanceWhenManualAndAutomaticTagsCollide() = withDiary { tags, _, id ->
        tags.edit(id, listOf("压力"))
        tags.apply(assertNotNull(tags.prepare(id)), listOf("焦虑"))
        tags.merge("压力", "焦虑")
        assertTrue(tags.state(id).automatic.isEmpty())
    }
    @Test
    fun automaticTagsAreCanonicalDistinctAndLimitedToThree() = withDiary { tags, _, id ->
        val snapshot = assertNotNull(tags.prepare(id))
        tags.apply(snapshot, listOf("职业抉择", "职业选择", "读书", "健身锻炼", "旅行"))
        assertEquals(listOf("职业选择", "读书", "健身"), tags.tags(id))
        assertNull(tags.prepare(id))
    }

    @Test
    fun manualTagsTakeSlotsAndRemovedAutomaticTagsStayRemoved() = withDiary { tags, _, id ->
        tags.edit(id, listOf("家庭"))
        tags.apply(assertNotNull(tags.prepare(id)), listOf("读书", "旅行", "健身"))
        assertEquals(listOf("家庭", "读书", "旅行"), tags.tags(id))
        tags.edit(id, listOf("家庭", "旅行"))
        tags.apply(assertNotNull(tags.prepare(id, force = true)), listOf("读书", "旅行", "健身"))
        assertEquals(listOf("家庭", "旅行", "健身"), tags.tags(id))
    }

    @Test
    fun lateResultsCannotOverwriteContentOrManualEdits() = withDiary { tags, diaries, id ->
        val snapshot = assertNotNull(tags.prepare(id))
        tags.edit(id, listOf("焦虑"))
        assertFalse(tags.apply(snapshot, listOf("健身")))
        val second = assertNotNull(tags.prepare(id))
        diaries.update(id, "新的正文", "焦虑", null, null)
        assertFalse(tags.apply(second, listOf("旅行")))
        assertEquals(listOf("焦虑"), tags.tags(id))
    }

    @Test
    fun disabledAutomaticTaggingDiscardsInFlightResultsButAllowsManualGeneration() = withDiary { tags, _, id ->
        val snapshot = assertNotNull(tags.prepare(id))
        tags.setEnabled(false)
        assertFalse(tags.apply(snapshot, listOf("读书")))
        assertNull(tags.prepare(id))
        assertNotNull(tags.prepare(id, force = true))
    }

    @Test
    fun mergeIsExplicitUpdatesSearchAndCanBeUndone() = withDiary { tags, diaries, id ->
        tags.edit(id, listOf("压力", "焦虑"))
        assertEquals(1, tags.mergeImpact("压力", "焦虑"))
        tags.merge("压力", "焦虑")
        assertEquals(listOf("焦虑"), tags.tags(id))
        assertEquals("焦虑", tags.canonical("压力"))
        assertEquals(1, diaries.searchSync("焦虑").size)
        assertEquals(1, diaries.searchSync("压力").size)
        assertTrue(tags.undoMerge())
        assertEquals(listOf("压力", "焦虑"), tags.tags(id))
        assertEquals("压力", tags.canonical("压力"))
    }

    @Test
    fun exactTagFilteringDoesNotMatchRelatedLongerNames() {
        assertFalse(matchesDiaryTag("工作效率,学习", "工作", DiaryTagVocabulary()))
        assertTrue(matchesDiaryTag("感悟,学习", "感想", DiaryTagVocabulary()))
    }

    @Test
    fun modelCannotCreateASynonymByMarkingItAsNewTopic() = runBlocking {
        val result = DiaryTagGenerator { _, _ ->
            """{"tags":[{"name":"职场抉择","newTopic":true}],"merges":[{"from":"职场抉择","to":"职业选择"}]}"""
        }.generate("考虑换工作", listOf("职业选择"), emptyMap())
        assertTrue(result.tags.isEmpty())
    }

    @Test
    fun metadataSurvivesReopeningRepositoryAndTagUpdatesDoNotChangeDiaryTimestamp() = withDiary { tags, diaries, id ->
        val before = assertNotNull(diaries.getById(id)).updated_at
        tags.apply(assertNotNull(tags.prepare(id)), listOf("读书"))
        assertEquals(before, assertNotNull(diaries.getById(id)).updated_at)
        tags.edit(id, emptyList())
        assertTrue(tags.state(id).suppressed.contains("读书"))
    }

    @Test
    fun parserRejectsUnapprovedSynonymsAndAcceptsDistinctNewTopics() = runBlocking {
        var requests = 0
        val generator = DiaryTagGenerator { _, _ ->
            requests++
            if (requests == 1) """{"tags":[{"name":"职业选择"},{"name":"职业抉择"},{"name":"感悟","newTopic":true},{"name":"园艺","newTopic":true}],"merges":[{"from":"职业抉择","to":"职业选择"}]}"""
            else """{"reviews":[{"name":"园艺","decision":"distinct"}]}"""
        }
        val result = generator.generate("今天思考换工作，种花。", listOf("职业选择"), emptyMap())
        assertEquals(listOf("职业选择", "园艺"), result.tags)
        assertEquals(listOf(DiaryTagMerge("职业抉择", "职业选择")), result.merges)
    }

    @Test
    fun invalidAiResponsePreservesExistingTags() = withDiary { tags, _, id ->
        tags.edit(id, listOf("家庭"))
        assertFailsWith<IllegalArgumentException> {
            runBlocking { DiaryTagGenerator { _, _ -> "not json" }.generate("正文", emptyList(), emptyMap()) }
        }
        assertEquals(listOf("家庭"), tags.tags(id))
    }

    private fun withDiary(block: (DiaryTagRepository, DiaryRepository, Long) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val diaries = DiaryRepository(db, driver)
            val id = runBlocking { diaries.create("今天思考职业选择，也读书和锻炼。") }
            block(DiaryTagRepository(db, com.dailysatori.data.repository.DiaryThreadRepository(db, driver)), diaries, id)
        } finally {
            driver.close()
        }
    }
}

package com.dailysatori.ui.feature.ideatopic

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.ideatopic.IdeaTopicError
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import com.dailysatori.service.ideatopic.IdeaCapturedEventPayload
import com.dailysatori.service.ideatopic.IdeaContentUpdatedEventPayload
import com.dailysatori.service.ideatopic.IdeaDraftEventPayload
import com.dailysatori.service.ideatopic.IdeaDraftResolvedEventPayload
import com.dailysatori.service.ideatopic.IdeaEventKinds
import com.dailysatori.service.ideatopic.IdeaMergedEventPayload
import com.dailysatori.service.ideatopic.IdeaProgressEventPayload
import com.dailysatori.service.ideatopic.IdeaSourceKey
import com.dailysatori.service.ideatopic.IdeaSourceSnapshot
import com.dailysatori.service.ideatopic.IdeaSourceTypes
import com.dailysatori.service.ideatopic.IdeaStatusChangedEventPayload
import com.dailysatori.service.ideatopic.IdeaTopicContent
import com.dailysatori.service.ideatopic.IdeaTopicEvent
import com.dailysatori.service.ideatopic.IdeaTopicStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IdeaTopicPresentationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun timelineAndErrorsUseTheSelectedLanguage() {
        withTranslations("en") { i18n ->
            val event = IdeaTopicEvent("event", "main", "old", IdeaEventKinds.Progress, "{\"text\":\"Research done\"}", 1)
            val presentation = formatIdeaTopicEvent(event, "main", translate = { i18n.t(it) })
            assertEquals("Progress update", presentation.title)
            assertTrue(presentation.originalAttribution!!.contains("old"))
            IdeaTopicError.entries.forEach { error ->
                val key = ideaTopicErrorLabelKey(error)
                assertTrue(i18n.t(key) != key, "an error must show a translated explanation: $error")
            }
        }
    }

    private fun <T> withTranslations(language: String, block: (I18nService) -> T): T {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val i18n = I18nService(SettingRepository(DailySatoriDatabase(driver)))
            val path = listOf(File("../shared/src/commonMain/resources/i18n/$language.yaml"), File("shared/src/commonMain/resources/i18n/$language.yaml")).first { it.exists() }
            i18n.loadTranslation(language, path.readText())
            i18n.init(language)
            return block(i18n)
        } finally { driver.close() }
    }

    private fun localizedEvent(event: IdeaTopicEvent, currentTopicId: String): IdeaEventPresentation =
        withTranslations("zh") { i18n -> formatIdeaTopicEvent(event, currentTopicId, translate = { i18n.t(it) }) }

    @Test
    fun capturedEventFormatsReadableTitleAndPreservesOriginalAttribution() {
        val payload = IdeaCapturedEventPayload(
            snapshot = IdeaSourceSnapshot(
                key = IdeaSourceKey(IdeaSourceTypes.Diary, "101"),
                originalTitle = "日记原始想法",
                originalContent = "完整的日记正文内容...",
                originalRecordId = "101",
            ),
            initialContent = IdeaTopicContent(title = "日记原始想法"),
        )
        val event = IdeaTopicEvent(
            id = "ev-1",
            topicId = "topic-main",
            originalTopicId = "topic-old",
            kind = IdeaEventKinds.Captured,
            payload = json.encodeToString(payload),
            createdAt = 1_000L,
        )

        val presentation = localizedEvent(event, currentTopicId = "topic-main")
        assertTrue(presentation.title.contains("日记"))
        assertTrue(presentation.description.contains("日记原始想法"))
        assertNotNull(presentation.originalAttribution)
        assertTrue(presentation.originalAttribution!!.contains("topic-old"))
        assertFalse(presentation.description.startsWith("{"), "Must not display raw unparsed JSON")
    }

    @Test
    fun progressEventFormatsTextDirectly() {
        val payload = IdeaProgressEventPayload(text = "完成了技术选型与原型验证")
        val event = IdeaTopicEvent(
            id = "ev-2",
            topicId = "topic-main",
            originalTopicId = "topic-main",
            kind = IdeaEventKinds.Progress,
            payload = json.encodeToString(payload),
            createdAt = 2_000L,
        )

        val presentation = localizedEvent(event, currentTopicId = "topic-main")
        assertTrue(presentation.title.contains("推进") || presentation.title.contains("进展"))
        assertEquals("完成了技术选型与原型验证", presentation.description)
    }

    @Test
    fun mergedEventFormatsSourceAndTargetTopics() {
        val payload = IdeaMergedEventPayload(
            fromTopicId = "topic-a",
            fromTitle = "待合并主题A",
            fromContent = IdeaTopicContent(title = "待合并主题A"),
            intoTopicId = "topic-b",
            intoTitle = "主主题B",
            intoContent = IdeaTopicContent(title = "主主题B"),
        )
        val event = IdeaTopicEvent(
            id = "ev-3",
            topicId = "topic-b",
            originalTopicId = "topic-a",
            kind = IdeaEventKinds.Merged,
            payload = json.encodeToString(payload),
            createdAt = 3_000L,
        )

        val presentation = localizedEvent(event, currentTopicId = "topic-b")
        assertTrue(presentation.title.contains("合并"))
        assertTrue(presentation.description.contains("待合并主题A"))
    }

    @Test
    fun contentUpdatedEventFormatsReadableFieldSummary() {
        val payload = IdeaContentUpdatedEventPayload(
            before = IdeaTopicContent(title = "旧标题", description = "旧描述"),
            after = IdeaTopicContent(title = "新标题", description = "新描述"),
        )
        val event = IdeaTopicEvent(
            id = "ev-4",
            topicId = "topic-1",
            originalTopicId = "topic-1",
            kind = IdeaEventKinds.ContentUpdated,
            payload = json.encodeToString(payload),
            createdAt = 4_000L,
        )

        val presentation = localizedEvent(event, currentTopicId = "topic-1")
        assertTrue(presentation.title.contains("更新") || presentation.title.contains("修订"))
        assertTrue(presentation.description.contains("标题") || presentation.description.contains("新标题"))
    }

    @Test
    fun statusLabelKeysMapCorrectly() {
        assertEquals("idea_topic.status.to_research", ideaTopicStatusLabelKey(IdeaTopicStatus.PendingResearch))
        assertEquals("idea_topic.status.researching", ideaTopicStatusLabelKey(IdeaTopicStatus.Researching))
        assertEquals("idea_topic.status.in_progress", ideaTopicStatusLabelKey(IdeaTopicStatus.Advancing))
        assertEquals("idea_topic.status.completed", ideaTopicStatusLabelKey(IdeaTopicStatus.Completed))
    }
}

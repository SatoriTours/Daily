package com.dailysatori.ui.feature.myspace

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.IdeaTopicRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.ideatopic.*
import com.dailysatori.service.opportunity.*
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.produceIn
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(FlowPreview::class)
class OpportunityTopicIntegrationTest {
    @Test
    fun anOpenOpportunityListReactsToCaptureAndJoiningAnExistingTopic() = runBlocking {
        val fixture = OpportunityFixture()
        try {
            val saved = sampleOpportunity("saved", saved = true)
            val plain = sampleOpportunity("plain")
            fixture.seed(saved, plain)
            assertTrue(fixture.awaitLinks { it.isEmpty() }.isEmpty())
            assertTrue(hasLegacySavedOpportunities(fixture.service.state.value.items))

            val topic = fixture.topics.capture(opportunityIdeaCaptureInput(saved)).topicId
            val captured = fixture.awaitLinks { it["saved"] == topic }
            assertFalse(hasLegacySavedOpportunities(fixture.service.state.value.items, captured))
            fixture.assertSavedUnchanged("saved")

            fixture.topics.capture(opportunityIdeaCaptureInput(plain).copy(targetTopicId = topic))
            val joined = fixture.awaitLinks { it["plain"] == topic }
            assertEquals(mapOf("saved" to topic, "plain" to topic), joined)
            fixture.assertSavedUnchanged("saved")
        } finally { fixture.close() }
    }

    @Test
    fun anOpenOpportunityListFollowsMergesAndRestoresLegacyItemsAfterDeletion() = runBlocking {
        val fixture = OpportunityFixture()
        try {
            val saved = sampleOpportunity("saved", saved = true)
            val target = sampleOpportunity("target")
            fixture.seed(saved, target)
            val originalTopic = fixture.topics.capture(opportunityIdeaCaptureInput(saved)).topicId
            val targetTopic = fixture.topics.capture(opportunityIdeaCaptureInput(target)).topicId
            fixture.awaitLinks { it["saved"] == originalTopic && it["target"] == targetTopic }

            fixture.topics.merge(originalTopic, targetTopic)
            val merged = fixture.awaitLinks { it["saved"] == targetTopic }
            assertEquals(targetTopic, merged["target"])
            fixture.assertSavedUnchanged("saved")

            fixture.topics.delete(targetTopic)
            val deleted = fixture.awaitLinks { it.isEmpty() }
            assertEquals(listOf("saved"), opportunityItems(fixture.service.state.value.items,
                OpportunityFilter.SAVED, deleted).map { it.id })
            fixture.assertSavedUnchanged("saved")
        } finally { fixture.close() }
    }

    @Test
    fun opportunityDataChangesRefreshLinksWithoutAnyTopicMutation() = runBlocking {
        val fixture = OpportunityFixture()
        try {
            val entry = sampleOpportunity("later")
            val topic = fixture.topics.capture(opportunityIdeaCaptureInput(entry)).topicId
            fixture.awaitLinks { it.isEmpty() }
            fixture.seed(entry)
            assertEquals(mapOf("later" to topic), fixture.awaitLinks { it["later"] == topic })
            fixture.seed()
            assertTrue(fixture.awaitLinks { it.isEmpty() }.isEmpty())
        } finally { fixture.close() }
    }

    @Test
    fun visibleFiltersHideLegacySavedWhenNoneArePending() {
        val saved = sampleOpportunity("saved", saved = true)
        val plain = sampleOpportunity("plain")
        assertEquals(OpportunityFilter.entries, visibleOpportunityFilters(listOf(saved, plain)))
        val withoutLegacy = listOf(OpportunityFilter.PENDING, OpportunityFilter.ACTED, OpportunityFilter.IGNORED)
        assertEquals(withoutLegacy, visibleOpportunityFilters(listOf(saved, plain), mapOf("saved" to "topic")))
        assertEquals(withoutLegacy, visibleOpportunityFilters(listOf(saved.copy(ignored = true))))
        assertEquals(withoutLegacy, visibleOpportunityFilters(listOf(plain)))
    }

    @Test
    fun opportunityActionsUseTheSelectedLanguage() {
        val fixture = OpportunityFixture()
        try {
            val i18n = I18nService(fixture.settings)
            val base = listOf(File("../shared/src/commonMain/resources/i18n"), File("shared/src/commonMain/resources/i18n"))
                .first { it.exists() }
            listOf("en", "zh").forEach { i18n.loadTranslation(it, File(base, "$it.yaml").readText()) }
            i18n.init("en")
            assertEquals("View Idea", i18n.t(opportunityActionLabelKey(true)))
            assertEquals("Capture as Idea", i18n.t(opportunityActionLabelKey(false)))
            i18n.init("zh")
            assertEquals("查看点子", i18n.t(opportunityActionLabelKey(true)))
            assertEquals("收为点子", i18n.t(opportunityActionLabelKey(false)))
        } finally { runBlocking { fixture.close() } }
    }
}

@OptIn(FlowPreview::class)
private class OpportunityFixture {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { DailySatoriDatabase.Schema.create(it) }
    private val database = DailySatoriDatabase(driver)
    val settings = SettingRepository(database)
    val topics = IdeaTopicService(IdeaTopicRepository(database))
    val service = NewsOpportunityService(NewsOpportunityStore(settings), OpportunityAnalyzer { null }, object : NewsOpportunityContext {
        override val enabled = false
        override fun verifiedContext(): String? = null
    })
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val links: ReceiveChannel<Map<String, String>> = observeOpportunityTopicLinks(service.state, topics)
        .distinctUntilChanged().produceIn(scope)

    suspend fun seed(vararg items: NewsOpportunity) {
        settings.upsert("news_opportunity_archive_v1", buildJsonObject {
            put("items", Json.encodeToJsonElement(items.toList()))
        }.toString())
        service.refresh()
    }

    suspend fun awaitLinks(predicate: (Map<String, String>) -> Boolean): Map<String, String> = withTimeout(5_000) {
        var value: Map<String, String>
        do { value = links.receive() } while (!predicate(value))
        value
    }

    fun assertSavedUnchanged(id: String) {
        val items = Json.parseToJsonElement(settings.get("news_opportunity_archive_v1")!!).jsonObject.getValue("items").jsonArray
        val saved = items.first { it.jsonObject.getValue("id").jsonPrimitive.content == id }.jsonObject
        assertTrue(saved.getValue("saved").jsonPrimitive.boolean)
        assertEquals(1_000L, saved.getValue("savedAt").jsonPrimitive.long)
    }

    suspend fun close() {
        scope.coroutineContext.job.cancelAndJoin()
        driver.close()
    }
}

private fun sampleOpportunity(id: String, saved: Boolean = false) = NewsOpportunity(
    id = id,
    article = ReadNewsArticle("art-$id", "标题 $id", "正文 $id", "https://example.com/$id", "测试来源", "2026-10-08", 1_000),
    title = "机会 $id", category = "科技", fact = "事实", relevance = "相关性", action = "行动", caveat = "注意", quote = "引用",
    createdAt = 1_000, saved = saved, savedAt = if (saved) 1_000 else null,
)

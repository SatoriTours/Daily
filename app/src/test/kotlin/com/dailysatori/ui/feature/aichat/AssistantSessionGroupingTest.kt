package com.dailysatori.ui.feature.aichat

import com.dailysatori.service.assistant.AssistantSessionSummary
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssistantSessionGroupingTest {
    private val zone = ZoneId.of("America/New_York")
    private val now = Instant.parse("2026-11-02T05:30:00Z").toEpochMilli()
    private fun session(id: String, date: String, title: String = id, preview: String = "正文独有词") =
        AssistantSessionSummary(id, title, preview, Instant.parse(date).toEpochMilli())

    @Test
    fun groupsUseCalendarDaysAcrossTheTwentyFiveHourDstDay() {
        val rows = listOf(
            session("week", "2026-10-28T04:30:00Z"),
            session("yesterday", "2026-11-01T04:30:00Z"),
            session("older", "2026-10-20T04:30:00Z"),
            session("today", "2026-11-02T05:15:00Z"),
        )
        val groups = groupAssistantSessions(rows, "", now, zone)
        assertEquals(AssistantSessionPeriod.entries.toList(), groups.map { it.period })
        assertEquals(listOf("today", "yesterday", "week", "older"), groups.flatMap { it.sessions }.map { it.id })
    }

    @Test
    fun searchesOnlyTitlesWithoutPretendingToSearchMessagePreviewsOrIds() {
        val rows = listOf(session("internal-uuid", "2026-11-02T05:15:00Z", "Reading 与阅读"))
        assertEquals(rows, groupAssistantSessions(rows, " reading ", now, zone).single().sessions)
        assertEquals(rows, groupAssistantSessions(rows, "阅读", now, zone).single().sessions)
        assertTrue(groupAssistantSessions(rows, "正文独有词", now, zone).isEmpty())
        assertTrue(groupAssistantSessions(rows, "internal-uuid", now, zone).isEmpty())
        assertEquals(rows, groupAssistantSessions(rows, "  ", now, zone).single().sessions)
    }

    @Test
    fun activitySortIsStableAndEmptyHistoryStillHasNoInventedBlankSession() {
        val rows = listOf(session("b", "2026-11-02T05:00:00Z"), session("a", "2026-11-02T05:00:00Z"))
        assertEquals(listOf("a", "b"), groupAssistantSessions(rows, "", now, zone).single().sessions.map { it.id })
        assertTrue(groupAssistantSessions(emptyList(), "", now, zone).isEmpty())
    }
}

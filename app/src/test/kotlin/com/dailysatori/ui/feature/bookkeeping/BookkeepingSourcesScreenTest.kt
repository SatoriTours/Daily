package com.dailysatori.ui.feature.bookkeeping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookkeepingSourcesScreenTest {
    private val sources = listOf(
        BookkeepingSource("com.payment", "Alipay"),
        BookkeepingSource("com.chat", "微信"),
        BookkeepingSource("com.sms", "短信"),
    )

    @Test fun selectedTabOnlyIncludesChosenAppsAndKeepsTheirOrder() {
        val selected = setOf("com.sms", "com.payment")
        assertEquals(listOf(sources[0], sources[2]), filterBookkeepingSources(sources, selected, "", true))
        assertEquals(sources, filterBookkeepingSources(sources, selected, "", false))
    }

    @Test fun searchIgnoresCaseAndSurroundingWhitespace() {
        assertEquals(listOf(sources[0]), filterBookkeepingSources(sources, emptySet(), "  ALIPAY  ", false))
        assertEquals(sources, filterBookkeepingSources(sources, emptySet(), "  ", false))
    }

    @Test fun searchWorksForChineseLabelsAndPackageNames() {
        assertEquals(listOf(sources[1]), filterBookkeepingSources(sources, emptySet(), "微", false))
        assertEquals(listOf(sources[2]), filterBookkeepingSources(sources, emptySet(), "COM.SMS", false))
    }

    @Test fun searchAndSelectedFilterIntersectWithoutChangingSavedSelections() {
        val selected = setOf("com.payment", "com.sms")
        assertTrue(filterBookkeepingSources(sources, selected, "微信", true).isEmpty())
        assertEquals(listOf(sources[2]), filterBookkeepingSources(sources, selected, "短信", true))
        assertEquals(setOf("com.payment", "com.sms"), selected)
    }
}

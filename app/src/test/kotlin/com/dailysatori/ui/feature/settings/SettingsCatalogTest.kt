package com.dailysatori.ui.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.io.File
import com.dailysatori.service.i18n.I18nService
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase

class SettingsCatalogTest {
    @Test fun homeHasElevenEntriesInFourGroupsWithoutDuplicatePages() {
        assertEquals(11, settingsHomeEntries.size)
        assertEquals(4, settingsHomeEntries.map { it.group }.distinct().size)
        assertEquals(11, settingsHomeEntries.map { it.destination.page }.distinct().size)
    }

    @Test fun relatedPagesArePeersAndReturnDirectlyToSettings() {
        assertEquals(listOf(SettingsPage.HOT_NEWS, SettingsPage.REMOTE_NEWS, SettingsPage.EXTERNAL_FAVORITES),
            SettingsPage.REMOTE_NEWS.groupPages())
        assertEquals("settings_design.sources", SettingsPage.HOT_NEWS.groupTitleKey())
        assertEquals(listOf(SettingsPage.BACKUP_SETTINGS, SettingsPage.BACKUP_RESTORE, SettingsPage.DATA_IMPORT),
            SettingsPage.BACKUP_RESTORE.groupPages())
        assertEquals(listOf(SettingsPage.AI_CONFIG, SettingsPage.AI_PURPOSE, SettingsPage.SPEECH), SettingsPage.SPEECH.groupPages())
        SettingsPage.entries.forEach { assertEquals(SettingsPage.MAIN, it.parent()) }
    }

    @Test fun searchFindsPermissionSectionAndEnglishKeywords() {
        val sms = searchSettings(" 短信权限 ") { it }.single()
        assertEquals(SettingsDestination(SettingsPage.AUTHORIZATION, "sms"), sms.destination)
        assertTrue(searchSettings("API KEY") { it }.any { it.destination.page == SettingsPage.AI_CONFIG })
        listOf("GitHub", "Hacker News", "小红书", "今日头条", "知乎", "V2EX", "Linux DO", "RSSHub").forEach { query ->
            assertTrue(searchSettings(query) { it }.any { it.destination.page == SettingsPage.HOT_NEWS })
        }
        assertTrue(searchSettings("restore") { it }.any { it.destination.page == SettingsPage.BACKUP_RESTORE })
        assertTrue(searchSettings("zz-not-a-setting") { it }.isEmpty())
        assertTrue(searchSettings("  ") { it }.isEmpty())
    }

    @Test fun localizedTitlesAreSearchable() {
        assertTrue(searchSettings("Voix") { if (it == "settings_design.speech") "Voix" else it }
            .any { it.destination.page == SettingsPage.SPEECH })
    }

    @Test fun settingsTranslationsParseAndMatchPackagedResources() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            val service = I18nService(SettingRepository(DailySatoriDatabase(driver)))
            val keySets = listOf("zh", "en").map { lang ->
                val asset = File("src/main/assets/i18n/$lang.yaml").readText()
                val shared = File("../shared/src/commonMain/resources/i18n/$lang.yaml").readText()
                fun section(content: String, root: String) = content.substringAfter("\n$root:\n")
                    .lineSequence().takeWhile { it.isBlank() || it.startsWith(" ") }.joinToString("\n")
                service.loadTranslation(lang, asset)
                service.init(lang)
                listOf("settings_design", "news_sources").flatMap { root ->
                    val translated = section(asset, root)
                    assertTrue(translated.isNotBlank())
                    assertEquals(translated, section(shared, root))
                    Regex("^  ([a-z_]+):", RegexOption.MULTILINE).findAll(translated).map { match ->
                        val key = "$root.${match.groupValues[1]}"
                        assertFalse(service.t(key) == key, "Missing translation: $lang/$key")
                        key
                    }.toList()
                }.toSet()
            }
            assertEquals(keySets[0], keySets[1])
        }
    }
}

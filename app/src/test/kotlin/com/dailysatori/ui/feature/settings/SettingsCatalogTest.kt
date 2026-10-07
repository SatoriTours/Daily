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
        assertEquals(listOf(SettingsPage.BACKUP_SETTINGS, SettingsPage.BACKUP_RESTORE, SettingsPage.DATA_IMPORT),
            SettingsPage.BACKUP_RESTORE.groupPages())
        assertEquals(listOf(SettingsPage.AI_CONFIG, SettingsPage.AI_PURPOSE, SettingsPage.SPEECH), SettingsPage.SPEECH.groupPages())
        SettingsPage.entries.forEach { assertEquals(SettingsPage.MAIN, it.parent()) }
    }

    @Test fun searchFindsPermissionSectionAndEnglishKeywords() {
        val sms = searchSettings(" 短信权限 ") { it }.single()
        assertEquals(SettingsDestination(SettingsPage.PHONE_ASSISTANT, "sms"), sms.destination)
        assertTrue(searchSettings("API KEY") { it }.any { it.destination.page == SettingsPage.AI_CONFIG })
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
                fun settingsSection(content: String) = content.substringAfter("\nsettings_design:\n")
                    .lineSequence().takeWhile { it.isBlank() || it.startsWith(" ") }.joinToString("\n")
                val section = settingsSection(asset)
                assertEquals(section, settingsSection(shared))
                service.loadTranslation(lang, asset)
                service.init(lang)
                Regex("^  ([a-z_]+):", RegexOption.MULTILINE).findAll(section).map { match ->
                    val key = "settings_design.${match.groupValues[1]}"
                    assertFalse(service.t(key) == key, "Missing translation: $lang/$key")
                    key
                }.toSet()
            }
            assertEquals(keySets[0], keySets[1])
        }
    }
}

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
    @Test fun homeHasElevenNavigableEntriesAndTwoDirectActionsInFourGroups() {
        assertEquals(13, settingsHomeEntries.size)
        assertEquals(4, settingsHomeEntries.map { it.group }.distinct().size)
        val navigable = settingsHomeEntries.filter { it.action == null }
        assertEquals(11, navigable.size)
        assertEquals(11, navigable.map { it.destination.page }.distinct().size)
    }

    @Test fun applicationGroupOffersDirectUpdateAndLogExportActions() {
        val application = settingsHomeEntries.filter { it.group == "settings_design.application" }
        assertEquals(
            listOf("settings_design.check_update", "settings_design.export_logs",
                "settings_design.updates", "settings_design.diagnostics"),
            application.map { it.title },
        )
        assertEquals(listOf(SettingsAction.CHECK_UPDATE, SettingsAction.EXPORT_LOGS, null, null),
            application.map { it.action })
    }

    @Test fun searchFindsDirectUpdateAndLogExportActions() {
        assertEquals(SettingsAction.CHECK_UPDATE, searchSettings("检查更新") { it }.single().action)
        val export = searchSettings("导出日志") { it }.single()
        assertEquals(SettingsAction.EXPORT_LOGS, export.action)
        assertEquals(SettingsPage.DIAGNOSTICS, export.destination.page)
    }

    @Test fun relatedPagesArePeersAndReturnDirectlyToSettings() {
        assertEquals(listOf(SettingsPage.HOT_NEWS, SettingsPage.REMOTE_NEWS, SettingsPage.EXTERNAL_FAVORITES),
            SettingsPage.REMOTE_NEWS.groupPages())
        assertEquals("settings_design.sources", SettingsPage.HOT_NEWS.groupTitleKey())
        assertEquals(listOf(SettingsPage.BACKUP_SETTINGS, SettingsPage.BACKUP_RESTORE),
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
        listOf("import", "导入", "Flutter").forEach { query ->
            assertTrue(searchSettings(query) { it }.isEmpty())
        }
        assertTrue(searchSettings("zz-not-a-setting") { it }.isEmpty())
        assertTrue(searchSettings("  ") { it }.isEmpty())
    }

    @Test fun updateProgressTranslationsFormatKnownAndUnknownBytes() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            val service = I18nService(SettingRepository(DailySatoriDatabase(driver)))
            service.loadTranslation("zh", File("src/main/assets/i18n/zh.yaml").readText())
            service.init("zh")
            assertEquals("正在下载 25% · 1.0 KB / 2.0 KB",
                service.t("settings_design.update_downloading_known", 25, "1.0 KB", "2.0 KB"))
            assertEquals("已下载 1.0 KB · 总大小未知",
                service.t("settings_design.update_downloading_unknown", "1.0 KB" as Any))
        }
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

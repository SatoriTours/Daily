package com.dailysatori.service.i18n

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class I18nServiceTest {
    @Test fun loadingEnglishAfterChineseKeepsDefaultDiagnosticsInChinese() = withSettings { settings ->
        val service = I18nService(settings)
        service.init()
        loadTranslations(service, "zh", "en")

        assertEquals("zh", service.getCurrentLanguage())
        assertEquals("诊断与日志", service.t("diagnostics.title"))
        assertEquals("导出最近半小时日志与崩溃现场", service.t("diagnostics.subtitle"))
        assertEquals("导出最近 30 分钟日志", service.t("diagnostics.exportRecent"))
        assertEquals("已保存到所选位置", service.t("diagnostics.saved"))
        assertEquals("取消", service.t("diagnostics.cancel"))
    }

    @Test fun savedLanguageWinsRegardlessOfTranslationLoadingOrder() = withSettings { settings ->
        settings.upsert("app_language", "en")
        val service = I18nService(settings)
        service.init()
        loadTranslations(service, "en", "zh")

        assertEquals("en", service.getCurrentLanguage())
        assertEquals("Diagnostics and logs", service.t("diagnostics.title"))
    }

    @Test fun changingLanguageSelectsTheCorrespondingLoadedTranslations() = withSettings { settings ->
        val service = I18nService(settings)
        loadTranslations(service, "zh", "en")
        service.setLanguage("zh")

        assertEquals("诊断与日志", service.t("diagnostics.title"))
        assertEquals("zh", settings.get("app_language"))
        service.setLanguage("en")
        assertEquals("Diagnostics and logs", service.t("diagnostics.title"))
    }

    @Test fun completionFeedbackStaysCompactForLongTasksWithLinks() = withSettings { settings ->
        val service = I18nService(settings)
        loadTranslations(service, "zh", "en")
        val content = "这个周末提醒我注册一个图区的 Google 账号，然后购买它的会员。".repeat(20) +
            "https://x.com/Lniosytest/status/210787423749277786"
        for (language in listOf("zh", "en")) {
            service.setLanguage(language)
            val message = service.t("reminder_ui.completed_message", *arrayOf(content))
            assertTrue(message.length <= 30, "Completion feedback must not repeat the task: $language")
            assertFalse(message.contains("https://"))
            assertTrue(service.t("reminder_ui.cycle_completed").length <= 30)
        }
    }

    private fun loadTranslations(service: I18nService, vararg languages: String) {
        languages.forEach { language ->
            service.loadTranslation(language, File("src/commonMain/resources/i18n/$language.yaml").readText())
        }
    }

    private fun withSettings(block: (SettingRepository) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            block(SettingRepository(DailySatoriDatabase(driver)))
        } finally {
            driver.close()
        }
    }
}

package com.dailysatori.service.i18n

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

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

package com.dailysatori.core.service

import android.content.Context
import android.content.res.Configuration
import com.dailysatori.service.i18n.I18nService
import java.util.Locale

object I18nInitializer {
    fun localizedContext(context: Context, language: String): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return context.createConfigurationContext(configuration)
    }

    fun init(context: Context, i18nService: I18nService) {
        i18nService.init()
        val langs = listOf("zh", "en")
        langs.forEach { lang ->
            try {
                val content = context.assets.open("i18n/$lang.yaml").bufferedReader().readText()
                i18nService.loadTranslation(lang, content)
            } catch (e: Exception) {
                com.dailysatori.core.diagnostics.SafeAndroidLog.e("I18nInitializer", "Failed to load $lang translations", e)
            }
        }
    }
}

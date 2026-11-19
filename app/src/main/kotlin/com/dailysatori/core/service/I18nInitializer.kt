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

    /** The selected language is needed before the first UI attach; resources are not. */
    fun initLanguage(i18nService: I18nService) {
        i18nService.init()
    }

    fun loadTranslations(context: Context, i18nService: I18nService) {
        val langs = listOf("zh", "en")
        langs.forEach { lang ->
            try {
                // The flat bundle is prepared with the source resources; YAML stays the source of truth.
                val bundle = runCatching {
                    context.assets.open("i18n/$lang.txt").bufferedReader().readText()
                }.getOrNull()
                if (bundle != null) {
                    i18nService.loadTranslationBundle(lang, bundle)
                    return@forEach
                }
                val content = context.assets.open("i18n/$lang.yaml").bufferedReader().readText()
                i18nService.loadTranslation(lang, content)
            } catch (e: Exception) {
                com.dailysatori.core.diagnostics.SafeAndroidLog.e("I18nInitializer", "Failed to load $lang translations", e)
            }
        }
    }
}

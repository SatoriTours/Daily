package com.dailysatori.core.service

import android.content.Context
import com.dailysatori.service.i18n.I18nService

object I18nInitializer {
    fun init(context: Context, i18nService: I18nService) {
        val langs = listOf("zh", "en")
        langs.forEach { lang ->
            try {
                val content = context.assets.open("i18n/$lang.yaml").bufferedReader().readText()
                i18nService.loadTranslation(lang, content)
            } catch (e: Exception) {
                android.util.Log.e("I18nInitializer", "Failed to load $lang translations", e)
            }
        }
        val savedLang = i18nService.getCurrentLanguage()
        i18nService.init(savedLang)
    }
}

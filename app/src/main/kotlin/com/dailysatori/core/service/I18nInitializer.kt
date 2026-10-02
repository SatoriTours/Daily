package com.dailysatori.core.service

import android.content.Context
import com.dailysatori.service.i18n.I18nService

object I18nInitializer {
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

package com.dailysatori.service.i18n

import com.dailysatori.data.repository.SettingRepository
import co.touchlab.kermit.Logger

class I18nService(private val settingRepo: SettingRepository) {
    private val log = Logger.withTag("I18n")
    private var translations: Map<String, Any> = emptyMap()
    private var currentLang: String = "zh"
    private val warnedKeys = mutableSetOf<String>()

    fun init() {
        currentLang = settingRepo.get("app_language") ?: detectSystemLanguage()
        loadTranslations(currentLang)
    }

    fun t(key: String, defaultValue: String? = null): String {
        val value = lookup(translations, key)
        if (value == null) {
            val default = defaultValue ?: key
            if (key !in warnedKeys) {
                log.w { "Missing i18n key: $key" }
                warnedKeys.add(key)
            }
            return default
        }
        return value
    }

    fun t(key: String, vararg args: Any?): String {
        val template = t(key)
        return template.format(*args)
    }

    fun setLanguage(lang: String) {
        currentLang = lang
        settingRepo.upsert("app_language", lang)
        loadTranslations(lang)
    }

    fun getCurrentLanguage(): String = currentLang

    private fun lookup(map: Map<String, Any>, key: String): String? {
        val parts = key.split(".")
        var current: Any? = map
        for (part in parts) {
            when (current) {
                is Map<*, *> -> current = current[part]
                else -> return null
            }
        }
        return current as? String
    }

    private fun loadTranslations(lang: String) {
        // Translations will be loaded from YAML resources at the platform level
        // and injected via initTranslations()
    }

    fun initTranslations(data: Map<String, Any>) {
        translations = data
    }

    private fun detectSystemLanguage(): String {
        // Platform-specific detection will be injected via expect/actual
        return "zh"
    }
}

fun String.t(vararg args: Any?): String {
    // This will be available via Koin injection in actual use
    // For now, return the key itself as fallback
    return this
}

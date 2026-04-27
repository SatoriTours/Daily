package com.dailysatori.service.i18n

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import com.dailysatori.data.repository.SettingRepository

class I18nService(private val settingRepo: SettingRepository) {
    private var translations: Map<String, Any> = emptyMap()
    private var currentLang: String = "zh"
    private val warnedKeys = mutableSetOf<String>()

    fun init(lang: String? = null) {
        currentLang = lang ?: settingRepo.get("app_language") ?: "zh"
    }

    fun loadTranslation(lang: String, yamlContent: String) {
        val data = Yaml.default.parseToYamlNode(yamlContent)
        translations = yamlNodeToMap(data)
        currentLang = lang
    }

    fun t(key: String, defaultValue: String? = null): String {
        val value = lookup(translations, key)
        if (value == null) {
            val default = defaultValue ?: key
            if (key !in warnedKeys) {
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

    private fun yamlNodeToMap(node: YamlNode): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        if (node is YamlMap) {
            for ((key, value) in node.entries) {
                result[key.content] = yamlValueToAny(value)
            }
        }
        return result
    }

    private fun yamlValueToAny(node: YamlNode): Any = when (node) {
        is YamlMap -> {
            val map = mutableMapOf<String, Any>()
            for ((key, value) in node.entries) {
                map[key.content] = yamlValueToAny(value)
            }
            map
        }
        is YamlScalar -> node.content
        else -> node.toString()
    }
}

fun String.t(vararg args: Any?): String = this

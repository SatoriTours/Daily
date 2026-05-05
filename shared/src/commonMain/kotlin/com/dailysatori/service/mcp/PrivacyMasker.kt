package com.dailysatori.service.mcp

class PrivacyMasker {
    private val replacements = linkedMapOf<String, String>()
    private val counters = mutableMapOf<String, Int>()

    fun mask(text: String): String {
        var result = text
        result = maskRegex(result, "SECRET", Regex("(?i)(?:\\b(?:api[_-]?key|token|secret|password)\\b|密码|密钥)\\s*[:=：是]?\\s*[A-Za-z0-9_\\-]{4,}"))
        result = maskRegex(result, "ID", Regex("\\b\\d{17}[0-9Xx]\\b"))
        result = maskRegex(result, "PHONE", Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)"))
        result = maskRegex(result, "PERSON", Regex("(?:我叫|姓名|名字|朋友|同事|老板|同学|老师)([\\u4e00-\\u9fa5]{2,4})")) { match ->
            match.groupValues[1]
        }
        return result
    }

    fun restore(text: String): String = replacements.entries.fold(text) { acc, (placeholder, value) ->
        acc.replace(placeholder, value)
    }

    private fun maskRegex(text: String, type: String, regex: Regex, valueOf: (MatchResult) -> String = { it.value }): String {
        var result = text
        regex.findAll(text).map { valueOf(it) }.distinct().forEach { value ->
            if (value.isBlank()) return@forEach
            val placeholder = placeholderFor(type, value)
            result = result.replace(value, placeholder)
        }
        return result
    }

    private fun placeholderFor(type: String, value: String): String {
        replacements.entries.firstOrNull { it.value == value }?.let { return it.key }
        val next = (counters[type] ?: 0) + 1
        counters[type] = next
        val placeholder = "[$type${'_' }$next]"
        replacements[placeholder] = value
        return placeholder
    }
}

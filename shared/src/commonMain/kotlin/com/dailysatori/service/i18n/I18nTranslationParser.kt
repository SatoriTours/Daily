package com.dailysatori.service.i18n

/**
 * Strict reader for the shipped translation resources, which only use `group:` headers and
 * `key: "text"` pairs indented by two spaces per level.
 *
 * Parsing the real YAML grammar costs about a second on every cold start, so startup reads the
 * resources through this subset reader and keeps the YAML parser as the fallback. Unsupported
 * syntax returns null instead of a partial map, so the fallback stays the only path for it.
 */
object I18nTranslationParser {
    fun parse(text: String): Map<String, Any>? {
        val root = LinkedHashMap<String, Any>()
        // levels[i] holds the keys written with i two-space steps of indentation.
        val levels = ArrayList<MutableMap<String, Any>>()
        levels.add(root)
        var entries = 0
        for (line in text.lineSequence()) {
            val row = line.trimEnd('\r')
            if (row.isBlank()) continue
            val body = row.trimStart(' ')
            if (body.startsWith("#")) continue
            val indentation = row.length - body.length
            if (indentation % 2 != 0) return null
            val depth = indentation / 2
            if (depth >= levels.size) return null
            val (key, value) = splitKey(body) ?: return null
            val target = levels[depth]
            while (levels.size > depth + 1) levels.removeAt(levels.size - 1)
            if (value == null) {
                val nested = LinkedHashMap<String, Any>()
                target[key] = nested
                levels.add(nested)
            } else {
                target[key] = decodeScalar(value) ?: return null
            }
            entries++
        }
        // A key without children is a null value in YAML; this reader cannot represent it.
        if (entries == 0 || hasEmptyGroup(root)) return null
        return root
    }

    private fun hasEmptyGroup(value: Any?): Boolean = when {
        value is Map<*, *> -> value.isEmpty() || value.values.any { hasEmptyGroup(it) }
        else -> false
    }

    /** Returns the key and its inline value, or a null value for a key that only opens a group. */
    private fun splitKey(body: String): Pair<String, String?>? {
        val colon = body.indexOf(':')
        if (colon <= 0) return null
        val key = body.substring(0, colon)
        if (!key.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }) return null
        val rest = body.substring(colon + 1)
        if (rest.isEmpty()) return key to null
        if (!rest.startsWith(" ")) return null
        val value = rest.trimStart(' ')
        return key to value.ifEmpty { null }
    }

    private fun decodeScalar(value: String): String? = when (value.first()) {
        '"' -> decodeDoubleQuoted(value)
        '\'' -> decodeSingleQuoted(value)
        else -> decodePlain(value)
    }

    private fun decodeDoubleQuoted(value: String): String? {
        if (value.length < 2 || !value.endsWith("\"") || isEscaped(value, value.length - 1)) return null
        val inner = value.substring(1, value.length - 1)
        val result = StringBuilder(inner.length)
        var index = 0
        while (index < inner.length) {
            val char = inner[index]
            when {
                char == '"' -> return null
                char != '\\' -> result.append(char)
                else -> {
                    if (index + 1 >= inner.length) return null
                    when (val escape = inner[index + 1]) {
                        '\\', '"', '/', '\'' -> result.append(escape)
                        'n' -> result.append('\n')
                        't' -> result.append('\t')
                        'r' -> result.append('\r')
                        '0' -> result.append('\u0000')
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'u' -> {
                            val hex = inner.substringOrNull(index + 2, index + 6) ?: return null
                            val code = hex.toIntOrNull(16) ?: return null
                            result.append(code.toChar())
                            index += 4
                        }
                        else -> return null
                    }
                    index++
                }
            }
            index++
        }
        return result.toString()
    }

    private fun decodeSingleQuoted(value: String): String? {
        if (value.length < 2 || !value.endsWith("'")) return null
        val inner = value.substring(1, value.length - 1)
        val result = StringBuilder(inner.length)
        var index = 0
        while (index < inner.length) {
            val char = inner[index]
            if (char != '\'') {
                result.append(char)
            } else {
                if (inner.getOrNull(index + 1) != '\'') return null
                result.append('\'')
                index++
            }
            index++
        }
        return result.toString()
    }

    private fun decodePlain(value: String): String? {
        if (value.isEmpty()) return null
        val first = value.first()
        if (first in "&*!|>%@`\"'[]{}#,") return null
        if ((first == '-' || first == '?' || first == ':') && (value.length == 1 || value[1] == ' ')) return null
        if (value.endsWith(':')) return null
        if (value.contains(" #")) return null
        if (value.contains('\t')) return null
        if (value.contains(": ")) return null
        return value
    }

    private fun isEscaped(value: String, index: Int): Boolean {
        var backslashes = 0
        var cursor = index - 1
        while (cursor >= 0 && value[cursor] == '\\') {
            backslashes++
            cursor--
        }
        return backslashes % 2 == 1
    }

    private fun String.substringOrNull(start: Int, end: Int): String? =
        if (start >= 0 && end <= length && start <= end) substring(start, end) else null
}

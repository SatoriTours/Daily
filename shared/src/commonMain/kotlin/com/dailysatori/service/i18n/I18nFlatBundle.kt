package com.dailysatori.service.i18n

/**
 * Runtime form of the translation resources: one `dotted.key<TAB>value` line per string.
 *
 * The bundle is prepared when the app is built (see the committed flat files next to the YAML), so startup
 * only splits lines instead of parsing YAML. [encode] and [decode] are inverses and both are covered
 * by tests that compare against the YAML parser.
 */
object I18nFlatBundle {
    private const val SEPARATOR = '\t'

    fun encode(parsed: Map<String, Any>): String {
        val out = StringBuilder()
        fun walk(key: String, value: Any?) {
            if (value is Map<*, *>) {
                value.forEach { (child, nested) -> walk(if (key.isEmpty()) child as String else "$key.$child", nested) }
                return
            }
            if (out.isNotEmpty()) out.append('\n')
            out.append(escape(key)).append(SEPARATOR).append(escape(value.toString()))
        }
        parsed.forEach { (key, value) -> walk(key, value) }
        return out.toString()
    }

    fun decode(bundle: String): Map<String, Any> {
        val root = LinkedHashMap<String, Any>()
        for (line in bundle.lineSequence()) {
            if (line.isEmpty()) continue
            val separator = line.indexOf(SEPARATOR)
            if (separator <= 0) continue
            val parts = unescape(line.substring(0, separator)).split('.')
            var current: MutableMap<String, Any> = root
            for (part in parts.dropLast(1)) {
                val existing = current[part]
                current = if (existing is MutableMap<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    existing as MutableMap<String, Any>
                } else {
                    LinkedHashMap<String, Any>().also { current[part] = it }
                }
            }
            current[parts.last()] = unescape(line.substring(separator + 1))
        }
        return root
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                '\r' -> append("\\r")
                else -> append(char)
            }
        }
    }

    private fun unescape(value: String): String {
        if (!value.contains('\\')) return value
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '\\' || index + 1 >= value.length) {
                out.append(char)
                index++
                continue
            }
            when (val escape = value[index + 1]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> out.append('\r')
                '\\' -> out.append('\\')
                else -> out.append(escape)
            }
            index += 2
        }
        return out.toString()
    }
}

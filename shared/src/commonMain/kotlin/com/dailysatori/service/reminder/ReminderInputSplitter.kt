package com.dailysatori.service.reminder

private val fragmentBoundary = Regex("[\\r\\n；;]|(?<=\\s)(?=\\d+[.、]\\s+)")
private val numberedItemPrefix = Regex("^\\s*\\d+[.、]\\s*")

fun splitReminderInput(text: String): List<ReminderInputFragment> {
    val seen = mutableSetOf<String>()
    return text.split(fragmentBoundary).mapIndexedNotNull { index, candidate ->
        val fragment = candidate.replace(numberedItemPrefix, "").trim()
        fragment.takeIf { it.isNotEmpty() && seen.add(it) }
            ?.let { ReminderInputFragment(index, it) }
    }
}

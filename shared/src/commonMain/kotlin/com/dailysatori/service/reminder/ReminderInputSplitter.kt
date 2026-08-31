package com.dailysatori.service.reminder

private val fragmentBoundary = Regex("[\\r\\n；;]|。+(?=\\s*(?:\\d{1,2}月|每年|每月|明天|后天).{0,24}提醒)|(?<=\\s)(?=\\d+(?:[.]\\s+|、))")
private val numberedItemPrefix = Regex("^\\s*\\d+[.、]\\s*")
private val punctuationOnly = Regex("^[\\p{P}\\s]+$")

fun splitReminderInput(text: String): List<ReminderInputFragment> {
    val seen = mutableSetOf<String>()
    return text.split(fragmentBoundary).mapIndexedNotNull { index, candidate ->
        val fragment = candidate.replace(numberedItemPrefix, "").trim().trim('。')
        fragment.takeIf { it.isNotEmpty() && !punctuationOnly.matches(it) && seen.add(it) }
            ?.let { ReminderInputFragment(index, it) }
    }
}

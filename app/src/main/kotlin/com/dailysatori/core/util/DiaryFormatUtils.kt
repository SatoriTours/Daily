package com.dailysatori.core.util

import com.dailysatori.shared.db.Diary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal fun diaryTags(value: String?): List<String> = cleanDiaryListValues(value)

internal fun diaryImagePaths(value: String?): List<String> = cleanDiaryListValues(value)

private fun cleanDiaryListValues(value: String?): List<String> =
    value?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && it != "null" }.orEmpty()

internal fun stripDiaryInlineTags(content: String): String {
    val lines = content.lines().toMutableList()
    var i = lines.lastIndex
    while (i >= 0) {
        val line = lines[i].trim()
        if (line.isEmpty()) {
            lines.removeAt(i)
            i--
            continue
        }
        val parts = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (parts.all { it.startsWith("#") }) {
            lines.removeAt(i)
            i--
        } else {
            break
        }
    }
    return lines.dropLastWhile { it.isBlank() }.joinToString("\n")
}

internal fun diaryMonthTitle(diary: Diary): String = diaryMonthTitle(diary.created_at)

internal fun diaryMonthTitle(timeMillis: Long): String {
    val calendar = calendarFor(timeMillis)
    return "${toChineseNumber(calendar.get(Calendar.MONTH) + 1)}月"
}

internal fun diaryMonthKey(diary: Diary): String = diaryMonthKey(diary.created_at)

internal fun diaryMonthKey(timeMillis: Long): String = formatDiaryDate(timeMillis, "yyyy-MM")

internal fun diaryDayKey(diary: Diary): String = diaryDayKey(diary.created_at)

internal fun diaryDayKey(timeMillis: Long): String = formatDiaryDate(timeMillis, "yyyy-MM-dd")

internal fun diaryMonthDayLabel(diary: Diary): String = diaryMonthDayLabel(diary.created_at)

internal fun diaryMonthDayLabel(timeMillis: Long): String {
    val calendar = calendarFor(timeMillis)
    return "${calendar.get(Calendar.MONTH) + 1} 月 ${calendar.get(Calendar.DAY_OF_MONTH)} 日"
}

internal fun diaryDateDayNumber(diary: Diary): String = diaryDateDayNumber(diary.created_at)

internal fun diaryDateDayNumber(timeMillis: Long): String =
    calendarFor(timeMillis).get(Calendar.DAY_OF_MONTH).toString()

internal fun diaryDateMonthLabel(diary: Diary): String = diaryDateMonthLabel(diary.created_at)

internal fun diaryDateMonthLabel(timeMillis: Long): String {
    val calendar = calendarFor(timeMillis)
    return "${toChineseNumber(calendar.get(Calendar.MONTH) + 1)}月"
}

internal fun diaryDateWeekLabel(diary: Diary): String = diaryDateWeekLabel(diary.created_at)

internal fun diaryDateWeekLabel(timeMillis: Long): String {
    return when (calendarFor(timeMillis).get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "周一"
        Calendar.TUESDAY -> "周二"
        Calendar.WEDNESDAY -> "周三"
        Calendar.THURSDAY -> "周四"
        Calendar.FRIDAY -> "周五"
        Calendar.SATURDAY -> "周六"
        else -> "周日"
    }
}

internal fun diaryDateCountLabel(
    diary: Diary,
    dayDiaryCount: Int,
    nowMillis: Long = System.currentTimeMillis(),
): String = diaryDateCountLabel(diary.created_at, dayDiaryCount, nowMillis)

internal fun diaryDateCountLabel(
    timeMillis: Long,
    dayDiaryCount: Int,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val relative = diaryRelativeDayLabel(timeMillis, nowMillis)
    val count = "$dayDiaryCount 篇"
    return if (relative.isBlank()) count else "$relative · $count"
}

internal fun diaryRelativeDayLabel(diary: Diary, nowMillis: Long = System.currentTimeMillis()): String =
    diaryRelativeDayLabel(diary.created_at, nowMillis)

internal fun diaryRelativeDayLabel(timeMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val today = diaryDayKey(nowMillis)
    val calendar = calendarFor(nowMillis)
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = formatDiaryDate(calendar.timeInMillis, "yyyy-MM-dd")
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val beforeYesterday = formatDiaryDate(calendar.timeInMillis, "yyyy-MM-dd")
    return when (diaryDayKey(timeMillis)) {
        today -> "今天"
        yesterday -> "昨天"
        beforeYesterday -> "前天"
        else -> ""
    }
}

internal fun diaryMonthSummary(diaries: List<Diary>): String {
    if (diaries.isEmpty()) return emptyDiaryMonthSentence()
    val tags = diaries.flatMap { diaryTags(it.tags) }.distinct().take(3)
    val tagText = tags.takeIf { it.isNotEmpty() }?.joinToString("、") ?: "一些普通但明亮的片刻"
    return "这个月的你把 $tagText 留了下来。照片负责记住画面，文字负责留下当时的心。"
}

internal fun emptyDiaryMonthSentence(monthIndex: Int = Calendar.getInstance().get(Calendar.MONTH)): String {
    val sentences = listOf(
        "这个月还没有留下文字。没关系，生活不是每天都要存档，偶尔只负责发光也很好。",
        "空白不是缺席，它只是给下一段故事留了点位置。",
        "这个月的纸页还很干净，等风、等光，也等你忽然想写的那一刻。",
    )
    return sentences[monthIndex % sentences.size]
}

internal fun toChineseNumber(value: Int): String {
    val units = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
    return when (value) {
        in 0..10 -> units[value]
        in 11..19 -> "十${units[value % 10]}"
        in 20..99 -> "${units[value / 10]}十${if (value % 10 == 0) "" else units[value % 10]}"
        else -> value.toString()
    }
}

private fun formatDiaryDate(timeMillis: Long, pattern: String): String =
    SimpleDateFormat(pattern, Locale.CHINA).format(Date(timeMillis))

private fun calendarFor(timeMillis: Long): Calendar =
    Calendar.getInstance().apply { timeInMillis = timeMillis }

package com.dailysatori.core.util

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

object TimeUtils {
    fun formatRelativeTime(epochMillis: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - epochMillis
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            seconds < 60 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            hours < 24 -> "${hours}小时前"
            days < 7 -> "${days}天前"
            days < 30 -> "${days / 7}周前"
            days < 365 -> "${days / 30}月前"
            else -> "${days / 365}年前"
        }
    }

    fun formatDate(epochMs: Long): String {
        val instant = Instant.ofEpochMilli(epochMs)
        val localDate = LocalDate.ofInstant(instant, ZoneId.systemDefault())
        return "${localDate.year}-${localDate.monthValue.toString().padStart(2, '0')}-${localDate.dayOfMonth.toString().padStart(2, '0')}"
    }

    fun formatDateTime(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    fun formatShortDateTime(epochMs: Long): String {
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }
}

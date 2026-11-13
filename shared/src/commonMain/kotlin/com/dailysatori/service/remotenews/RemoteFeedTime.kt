package com.dailysatori.service.remotenews

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Feed timestamps are RFC 822 ("Tue, 12 Nov 2026 08:00:00 GMT") while the rest of the app stores
 * ISO-8601 instants, so both shapes are normalized here. Unknown formats are dropped instead of
 * being presented as a wrong date.
 */
internal fun normalizeRemoteFeedTime(value: String?): String? {
    val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    runCatching { Instant.parse(raw) }.getOrNull()?.let { return it.toString() }
    val match = RFC822.matchEntire(raw) ?: return null
    val day = match.groupValues[1].toIntOrNull() ?: return null
    val month = MONTHS[match.groupValues[2].lowercase()] ?: return null
    val year = match.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it } ?: return null
    val hour = match.groupValues[4].toIntOrNull() ?: return null
    val minute = match.groupValues[5].toIntOrNull() ?: return null
    val second = match.groupValues[6].toIntOrNull() ?: 0
    return runCatching {
        LocalDateTime(year, month, day, hour, minute, second)
            .toInstant(timeZoneOf(match.groupValues[7], match.groupValues[8]))
            .toString()
    }.getOrNull()
}

private fun timeZoneOf(numericOffset: String, zoneName: String): TimeZone {
    val offset = numericOffset.ifBlank { ZONE_OFFSETS[zoneName.uppercase()].orEmpty() }
    return if (offset.isBlank()) {
        TimeZone.UTC
    } else {
        val normalized = if (offset.length == 5 && offset[3] != ':') {
            offset.substring(0, 3) + ":" + offset.substring(3)
        } else {
            offset
        }
        runCatching { TimeZone.of(normalized) }.getOrDefault(TimeZone.UTC)
    }
}

private val RFC822 = Regex(
    """^(?:[A-Za-z]{3,},\s*)?(\d{1,2})\s+([A-Za-z]{3})[a-z]*\s+(\d{2,4})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(?:([+-]\d{2}:?\d{2})|([A-Za-z]{1,5}))?""",
)

private val MONTHS = mapOf(
    "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
    "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
)

private val ZONE_OFFSETS = mapOf(
    "UT" to "+00:00", "UTC" to "+00:00", "GMT" to "+00:00", "Z" to "+00:00",
    "EST" to "-05:00", "EDT" to "-04:00", "CST" to "-06:00", "CDT" to "-05:00",
    "MST" to "-07:00", "MDT" to "-06:00", "PST" to "-08:00", "PDT" to "-07:00",
)

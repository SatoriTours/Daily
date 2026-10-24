package com.dailysatori.service.reminder

import com.dailysatori.data.repository.ReminderProfile
import com.dailysatori.data.repository.ReminderRepository
import com.dailysatori.data.repository.SettingRepository
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/** One settings interpretation for both the reminder UI and assistant business creation. */
fun resolveDefaultReminderProfile(repository: ReminderRepository, settings: SettingRepository): ReminderProfile {
    val id = settings.get("reminder.default_profile") ?: "builtin-standard"
    val selected = repository.getProfile(id) ?: when (id) {
        "builtin-strong" -> ReminderProfile(id, "Strong", ReminderProfileKind.STRONG, ReminderProfileSnapshot.strong())
        "builtin-gentle" -> ReminderProfile(id, "Gentle", ReminderProfileKind.GENTLE, ReminderProfileSnapshot.gentle())
        else -> ReminderProfile("builtin-standard", "Standard", ReminderProfileKind.STANDARD, ReminderProfileSnapshot.standard())
    }
    val base = selected.snapshot
    fun time(key: String, fallback: LocalTime) = settings.get(key)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: fallback
    val workDays = settings.get("reminder.work_days")?.split(',')?.mapNotNull {
        runCatching { DayOfWeek.valueOf(it) }.getOrNull()
    }?.toSet().orEmpty().ifEmpty { base.workDays }
    return selected.copy(snapshot = base.copy(
        sleepStart = time("reminder.sleep_start", base.sleepStart), sleepEnd = time("reminder.sleep_end", base.sleepEnd),
        workDays = workDays, workStart = time("reminder.work_start", base.workStart), workEnd = time("reminder.work_end", base.workEnd),
        soundEnabled = settings.get("reminder.sound")?.toBooleanStrictOrNull() ?: base.soundEnabled,
        vibrationEnabled = settings.get("reminder.vibration")?.toBooleanStrictOrNull() ?: base.vibrationEnabled,
        importance = settings.get("reminder.importance")?.let { runCatching { ReminderImportance.valueOf(it) }.getOrNull() } ?: base.importance,
        lockScreenVisibility = settings.get("reminder.visibility")?.let { runCatching { ReminderLockScreenVisibility.valueOf(it) }.getOrNull() } ?: base.lockScreenVisibility,
    ))
}

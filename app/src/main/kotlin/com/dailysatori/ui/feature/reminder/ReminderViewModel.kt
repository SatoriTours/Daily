package com.dailysatori.ui.feature.reminder

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.dailysatori.core.reminder.ReminderCoordinator
import com.dailysatori.data.repository.ReminderEdit
import com.dailysatori.data.repository.ReminderRepository
import com.dailysatori.data.repository.ReminderProfile
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderDraft
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderStatus
import com.dailysatori.service.reminder.ReminderImportance
import com.dailysatori.service.reminder.ReminderLockScreenVisibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.DayOfWeek

enum class ReminderDraftField { CONTENT, START_DATE, END_DATE, FIRST_TIME, ACTIVE_DAY_RULE, PROFILE, ADVANCED_PROFILE }

data class ReminderConfirmationPayload(
    val draft: ReminderDraft,
    val profileSnapshot: ReminderProfileSnapshot,
)

data class ReminderConfirmationStart(val state: ReminderDraftUiState, val accepted: Boolean)

enum class ReminderDraftNotice { NOTIFICATION_PERMISSION, FALLBACK_TIMING, SCHEDULE_FAILED, SAVE_FAILED }

data class ReminderDraftUiState(
    val id: String,
    val content: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val firstReminderTime: LocalTime?,
    val activeDayRule: ReminderActiveDayRule,
    val profile: ReminderProfileSnapshot?,
    val profileId: String? = null,
    val daytimeBackoffInput: String = profile?.daytimeDismissalBackoffMinutes?.joinToString(",").orEmpty(),
    val eveningIntervalInput: String = profile?.eveningIntervalMinutes?.toString().orEmpty(),
    val saving: Boolean = false,
    val confirmed: Boolean = false,
    val cancelled: Boolean = false,
    val notice: ReminderDraftNotice? = null,
) {
    val validationErrors: Set<ReminderDraftField>
        get() = buildSet {
            if (content.isBlank() || content.length > 2_000) add(ReminderDraftField.CONTENT)
            if (startDate == null) add(ReminderDraftField.START_DATE)
            if (endDate == null || startDate != null && endDate < startDate) add(ReminderDraftField.END_DATE)
            if (firstReminderTime == null) add(ReminderDraftField.FIRST_TIME)
            if (activeDayRule is ReminderActiveDayRule.SelectedWeekdays && activeDayRule.days.isEmpty()) add(ReminderDraftField.ACTIVE_DAY_RULE)
            if (profile == null) add(ReminderDraftField.PROFILE)
            if (parseDraftBackoff(daytimeBackoffInput) == null || !parseDraftEveningInterval(eveningIntervalInput).valid) add(ReminderDraftField.ADVANCED_PROFILE)
        }
    val canConfirm: Boolean get() = validationErrors.isEmpty() && !saving && !confirmed && !cancelled
    val shouldPersist: Boolean get() = !cancelled
    val absoluteDateTimeText: String
        get() = listOfNotNull(
            startDate?.let { "$it ${firstReminderTime ?: "--:--"}" },
            endDate?.takeIf { it != startDate }?.toString(),
        ).joinToString(" — ")

    fun editContent(value: String) = copy(content = value, notice = null)
    fun editDates(start: LocalDate?, end: LocalDate?) = copy(startDate = start, endDate = end, notice = null)
    fun editFirstTime(value: LocalTime?) = copy(firstReminderTime = value, notice = null)
    fun editActiveDayRule(value: ReminderActiveDayRule) = copy(activeDayRule = value, notice = null)
    fun editProfile(value: ReminderProfileSnapshot?) = copy(
        profile = value?.copy(),
        daytimeBackoffInput = value?.daytimeDismissalBackoffMinutes?.joinToString(",").orEmpty(),
        eveningIntervalInput = value?.eveningIntervalMinutes?.toString().orEmpty(),
        notice = null,
    )
    fun updateProfile(value: ReminderProfileSnapshot?) = copy(profile = value?.copy(), notice = null)
    fun selectProfile(value: ReminderProfile) = copy(
        profile = value.snapshot.copy(),
        profileId = value.id,
        daytimeBackoffInput = value.snapshot.daytimeDismissalBackoffMinutes.joinToString(","),
        eveningIntervalInput = value.snapshot.eveningIntervalMinutes?.toString().orEmpty(),
        notice = null,
    )
    fun editBackoffInput(value: String): ReminderDraftUiState {
        val parsed = parseDraftBackoff(value)
        return copy(
            daytimeBackoffInput = value,
            profile = if (parsed != null) profile?.copy(daytimeDismissalBackoffMinutes = parsed) else profile,
            notice = null,
        )
    }
    fun editEveningIntervalInput(value: String): ReminderDraftUiState {
        val parsed = parseDraftEveningInterval(value)
        return copy(
            eveningIntervalInput = value,
            profile = if (parsed.valid) profile?.copy(eveningIntervalMinutes = parsed.value) else profile,
            notice = null,
        )
    }
    fun beginConfirmation(): ReminderConfirmationStart =
        if (!canConfirm) ReminderConfirmationStart(this, false) else ReminderConfirmationStart(copy(saving = true), true)
    fun cancel() = copy(cancelled = true, saving = false)

    fun confirmationPayload(): ReminderConfirmationPayload? {
        if (validationErrors.isNotEmpty()) return null
        return ReminderConfirmationPayload(
            draft = ReminderDraft(id, content.trim(), startDate, endDate, firstReminderTime, activeDayRule, profile?.copy()),
            profileSnapshot = profile!!.copy(),
        )
    }

    companion object {
        fun from(
            draft: ReminderDraft,
            fallbackProfile: ReminderProfileSnapshot = ReminderProfileSnapshot.standard(),
            fallbackProfileId: String = "builtin-standard",
        ) = ReminderDraftUiState(
            id = draft.id,
            content = draft.content,
            startDate = draft.startDate,
            endDate = draft.endDate,
            firstReminderTime = draft.firstReminderTime,
            activeDayRule = draft.activeDayRule,
            profile = draft.profile?.copy(
                sleepStart = fallbackProfile.sleepStart,
                sleepEnd = fallbackProfile.sleepEnd,
                workDays = fallbackProfile.workDays,
                workStart = fallbackProfile.workStart,
                workEnd = fallbackProfile.workEnd,
                soundEnabled = fallbackProfile.soundEnabled,
                vibrationEnabled = fallbackProfile.vibrationEnabled,
                importance = fallbackProfile.importance,
                lockScreenVisibility = fallbackProfile.lockScreenVisibility,
            ) ?: fallbackProfile.copy(),
            profileId = draft.profile?.kind?.builtInId() ?: fallbackProfileId,
        )
    }
}

private data class ParsedInterval(val valid: Boolean, val value: Int?)

private fun parseDraftBackoff(value: String): List<Int>? {
    if (value.isBlank()) return emptyList()
    val tokens = value.split(',').map(String::trim)
    if (tokens.size > 8 || tokens.any(String::isEmpty)) return null
    return tokens.map { it.toIntOrNull() ?: return null }.takeIf { values -> values.all { it in 1..1_440 } }
}

private fun parseDraftEveningInterval(value: String): ParsedInterval {
    if (value.isBlank()) return ParsedInterval(true, null)
    val parsed = value.toIntOrNull()
    return ParsedInterval(parsed != null && parsed in 1..1_440, parsed?.takeIf { it in 1..1_440 })
}

private fun com.dailysatori.service.reminder.ReminderProfileKind.builtInId(): String? = when (this) {
    com.dailysatori.service.reminder.ReminderProfileKind.STRONG -> "builtin-strong"
    com.dailysatori.service.reminder.ReminderProfileKind.STANDARD -> "builtin-standard"
    com.dailysatori.service.reminder.ReminderProfileKind.GENTLE -> "builtin-gentle"
    com.dailysatori.service.reminder.ReminderProfileKind.CUSTOM -> null
}

enum class ReminderFilter { ACTIVE, PAUSED, COMPLETED, EXPIRED }

enum class ReminderAction { PAUSE, RESUME, EDIT, COMPLETE, DELETE, APPLY_LATEST_PROFILE }

fun filterReminders(reminders: List<Reminder>, filter: ReminderFilter): List<Reminder> = reminders.filter { reminder ->
    when (filter) {
        ReminderFilter.ACTIVE -> reminder.status in setOf(ReminderStatus.ACTIVE, ReminderStatus.NOTIFIED, ReminderStatus.DISMISSED)
        ReminderFilter.PAUSED -> reminder.status == ReminderStatus.PAUSED
        ReminderFilter.COMPLETED -> reminder.status == ReminderStatus.COMPLETED
        ReminderFilter.EXPIRED -> reminder.status == ReminderStatus.EXPIRED
    }
}

fun reminderActions(reminder: Reminder): List<ReminderAction> = when (reminder.status) {
    ReminderStatus.ACTIVE, ReminderStatus.NOTIFIED, ReminderStatus.DISMISSED -> listOf(ReminderAction.PAUSE, ReminderAction.EDIT, ReminderAction.COMPLETE, ReminderAction.DELETE, ReminderAction.APPLY_LATEST_PROFILE)
    ReminderStatus.PAUSED -> listOf(ReminderAction.RESUME, ReminderAction.EDIT, ReminderAction.COMPLETE, ReminderAction.DELETE, ReminderAction.APPLY_LATEST_PROFILE)
    ReminderStatus.COMPLETED, ReminderStatus.EXPIRED -> listOf(ReminderAction.DELETE)
    ReminderStatus.DRAFT -> emptyList()
}

data class ReminderUiState(
    val drafts: Map<String, ReminderDraftUiState> = emptyMap(),
    val filter: ReminderFilter = ReminderFilter.ACTIVE,
    val selectedReminderId: String? = null,
    val error: String? = null,
)

class ReminderViewModel(
    private val repository: ReminderRepository,
    private val coordinator: ReminderCoordinator,
    private val settingRepository: SettingRepository,
    private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(ReminderUiState())
    val state: StateFlow<ReminderUiState> = _state
    val reminders: StateFlow<List<Reminder>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val profiles: StateFlow<List<ReminderProfile>> = repository.observeProfiles()
        .map { custom -> builtInProfiles() + custom }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), builtInProfiles() + repository.profiles())
    val visibleReminders: StateFlow<List<Reminder>> = combine(reminders, state) { items, ui -> filterReminders(items, ui.filter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun registerDraft(draft: ReminderDraft) {
        _state.update { current ->
            if (draft.id in current.drafts) current else {
                val default = defaultProfile()
                current.copy(drafts = current.drafts + (draft.id to ReminderDraftUiState.from(draft, default.snapshot, default.id)))
            }
        }
    }

    fun updateDraft(id: String, transform: (ReminderDraftUiState) -> ReminderDraftUiState) {
        _state.update { current ->
            val draft = current.drafts[id] ?: return@update current
            current.copy(drafts = current.drafts + (id to transform(draft)))
        }
    }

    fun cancelDraft(id: String) = updateDraft(id) { it.cancel() }

    fun confirmDraft(id: String) {
        var payload: ReminderConfirmationPayload? = null
        _state.update { current ->
            val draft = current.drafts[id] ?: return@update current
            val start = draft.beginConfirmation()
            if (!start.accepted) return@update current
            payload = draft.confirmationPayload()
            current.copy(drafts = current.drafts + (id to start.state))
        }
        val confirmedPayload = payload ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existing = repository.get(id)
                if (existing == null) repository.createConfirmed(confirmedPayload.draft, confirmedPayload.profileSnapshot)
                var notice: ReminderDraftNotice? = deliveryNotice()
                try {
                    coordinator.recompute(id)
                } catch (_: Exception) {
                    notice = ReminderDraftNotice.SCHEDULE_FAILED
                }
                updateDraft(id) { it.copy(saving = false, confirmed = true, notice = notice) }
            } catch (_: Exception) {
                updateDraft(id) { it.copy(saving = false, notice = ReminderDraftNotice.SAVE_FAILED) }
            }
        }
    }

    fun setFilter(filter: ReminderFilter) {
        _state.update { it.copy(filter = filter) }
    }

    fun selectReminder(id: String?) {
        _state.update { it.copy(selectedReminderId = id) }
    }

    fun pause(id: String) = mutateAndRecompute(id) { repository.pause(id) }
    fun resume(id: String) = mutateAndRecompute(id) { repository.resume(id) }
    fun complete(id: String) = mutateAndRecompute(id) { repository.complete(id) }

    fun edit(id: String, edit: ReminderEdit) = mutateAndRecompute(id) { repository.update(id, edit) }

    fun applyLatestProfile(id: String, profile: ReminderProfileSnapshot) {
        val reminder = repository.get(id) ?: return
        edit(id, ReminderEdit(expectedVersion = reminder.version, profile = profile.copy()))
    }

    fun delete(id: String) = mutateAndRecompute(id) { repository.delete(id) }

    private fun mutateAndRecompute(id: String, mutation: () -> Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (mutation()) coordinator.recomputeAfterStateChange(id)
        }
    }

    private fun defaultProfile(): ReminderProfile {
        val defaultId = settingRepository.get("reminder.default_profile") ?: "builtin-standard"
        val selected = repository.getProfile(defaultId) ?: builtInProfiles().firstOrNull { it.id == defaultId } ?: builtInProfiles()[1]
        val base = selected.snapshot
        fun time(key: String, fallback: LocalTime) = settingRepository.get(key)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: fallback
        val workDays = settingRepository.get("reminder.work_days")?.split(',')?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }?.toSet().orEmpty().ifEmpty { base.workDays }
        return selected.copy(snapshot = base.copy(
            sleepStart = time("reminder.sleep_start", base.sleepStart),
            sleepEnd = time("reminder.sleep_end", base.sleepEnd),
            workDays = workDays,
            workStart = time("reminder.work_start", base.workStart),
            workEnd = time("reminder.work_end", base.workEnd),
            soundEnabled = settingRepository.get("reminder.sound")?.toBooleanStrictOrNull() ?: base.soundEnabled,
            vibrationEnabled = settingRepository.get("reminder.vibration")?.toBooleanStrictOrNull() ?: base.vibrationEnabled,
            importance = settingRepository.get("reminder.importance")?.let { runCatching { ReminderImportance.valueOf(it) }.getOrNull() } ?: base.importance,
            lockScreenVisibility = settingRepository.get("reminder.visibility")?.let { runCatching { ReminderLockScreenVisibility.valueOf(it) }.getOrNull() } ?: base.lockScreenVisibility,
        ))
    }

    private fun builtInProfiles() = listOf(
        ReminderProfile("builtin-strong", "Strong", com.dailysatori.service.reminder.ReminderProfileKind.STRONG, ReminderProfileSnapshot.strong()),
        ReminderProfile("builtin-standard", "Standard", com.dailysatori.service.reminder.ReminderProfileKind.STANDARD, ReminderProfileSnapshot.standard()),
        ReminderProfile("builtin-gentle", "Gentle", com.dailysatori.service.reminder.ReminderProfileKind.GENTLE, ReminderProfileSnapshot.gentle()),
    )

    private fun deliveryNotice(): ReminderDraftNotice? {
        val runtimePermission = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!runtimePermission || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return ReminderDraftNotice.NOTIFICATION_PERMISSION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            return ReminderDraftNotice.FALLBACK_TIMING
        }
        return null
    }
}

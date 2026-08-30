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
import com.dailysatori.service.reminder.ReminderProfileKind
import com.dailysatori.service.reminder.ReminderStatus
import com.dailysatori.service.reminder.ReminderImportance
import com.dailysatori.service.reminder.ReminderLockScreenVisibility
import com.dailysatori.service.reminder.ReminderRecurrence
import com.dailysatori.service.reminder.LeapDayPolicy
import com.dailysatori.service.reminder.ReminderTextInterpreter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.util.UUID

data class ReminderEditorState(
    val content: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val firstReminderTime: LocalTime,
    val activeDayRule: ReminderActiveDayRule = ReminderActiveDayRule.Daily,
    val recurrence: ReminderRecurrence = ReminderRecurrence.Once,
    val profile: ReminderProfileSnapshot = ReminderProfileSnapshot.standard(),
    val leapDayFallbackChosen: Boolean = true,
    val saving: Boolean = false,
    val notice: String? = null,
) {
    fun selectMode(mode: ReminderEditorMode): ReminderEditorState = when (mode) {
        ReminderEditorMode.ONCE -> copy(recurrence = ReminderRecurrence.Once, activeDayRule = ReminderActiveDayRule.Daily)
        ReminderEditorMode.MONTHLY -> copy(recurrence = ReminderRecurrence.Monthly(startDate.dayOfMonth), activeDayRule = ReminderActiveDayRule.Daily)
        ReminderEditorMode.YEARLY -> copy(
            recurrence = ReminderRecurrence.Yearly(startDate.monthNumber, startDate.dayOfMonth, LeapDayPolicy.FEBRUARY_28),
            activeDayRule = ReminderActiveDayRule.Daily,
            leapDayFallbackChosen = startDate.monthNumber != 2 || startDate.dayOfMonth != 29,
        )
        ReminderEditorMode.CONSECUTIVE -> copy(
            recurrence = ReminderRecurrence.Once,
            activeDayRule = ReminderActiveDayRule.ConsecutiveDateRange,
        )
    }

    val validationMessage: String?
        get() = when {
            content.isBlank() -> "\u8bf7\u586b\u5199\u63d0\u9192\u5185\u5bb9\u3002"
            endDate < startDate -> "\u7ed3\u675f\u65e5\u671f\u4e0d\u80fd\u65e9\u4e8e\u5f00\u59cb\u65e5\u671f\u3002"
            activeDayRule is ReminderActiveDayRule.SelectedWeekdays && activeDayRule.days.isEmpty() -> "\u8bf7\u81f3\u5c11\u9009\u62e9\u4e00\u4e2a\u661f\u671f\u3002"
            recurrence is ReminderRecurrence.Yearly && (recurrence as ReminderRecurrence.Yearly).month == 2 && (recurrence as ReminderRecurrence.Yearly).dayOfMonth == 29 && !leapDayFallbackChosen -> "\u8bf7\u6307\u5b9a\u975e\u95f0\u5e74\u7684 2 \u6708 29 \u65e5\u5904\u7406\u65b9\u5f0f\u3002"
            else -> null
        }
    val canSave: Boolean get() = validationMessage == null && !saving

    fun actualBehaviorSummary(): String {
        val date = if (activeDayRule is ReminderActiveDayRule.ConsecutiveDateRange) {
            "\u8fde\u7eed${startDate.monthNumber}\u6708${startDate.dayOfMonth}\u65e5\u81f3${endDate.monthNumber}\u6708${endDate.dayOfMonth}\u65e5"
        } else when (val rule = recurrence) {
            ReminderRecurrence.Once -> if (startDate == endDate) "${startDate.monthNumber}\u6708${startDate.dayOfMonth}\u65e5" else "${startDate.monthNumber}\u6708${startDate.dayOfMonth}\u65e5\u81f3${endDate.monthNumber}\u6708${endDate.dayOfMonth}\u65e5"
            is ReminderRecurrence.Monthly -> "\u6bcf\u6708${rule.dayOfMonth}\u65e5"
            is ReminderRecurrence.Yearly -> "\u6bcf\u5e74${rule.month}\u6708${rule.dayOfMonth}\u65e5\u81f3${if (endDate.monthNumber == rule.month) "" else "${endDate.monthNumber}\u6708"}${endDate.dayOfMonth}\u65e5"
        }
        return "$date\uff0c${firstReminderTime.toString().padEnd(5, '0')}\u5f00\u59cb\u63d0\u9192\uff1b\u5de5\u4f5c\u65f6\u95f4\u4ec5\u663e\u793a\u901a\u77e5\uff0c\u4e0d\u64ad\u653e\u58f0\u97f3\u3002"
    }

    companion object {
        fun createDefault(profile: ReminderProfileSnapshot = ReminderProfileSnapshot.standard()): ReminderEditorState {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            return ReminderEditorState("", today, today, LocalTime(9, 0), profile = profile)
        }

        fun from(reminder: Reminder) = ReminderEditorState(
            content = reminder.content,
            startDate = reminder.startDate,
            endDate = reminder.endDate,
            firstReminderTime = reminder.firstReminderTime,
            activeDayRule = reminder.activeDayRule,
            recurrence = reminder.recurrence,
            profile = reminder.profile,
        )
    }
}

data class ReminderAiParseState(
    val prompt: String = "",
    val isInterpreting: Boolean = false,
    val submitCount: Int = 0,
    val error: String? = null,
) {
    fun onPromptChanged(value: String) = copy(prompt = value, error = null)
    fun onExplicitSubmit() = copy(isInterpreting = true, submitCount = submitCount + 1, error = null)
    fun onInterpretationFinished(error: String? = null) = copy(isInterpreting = false, error = error)
}

enum class ReminderEditorMode { ONCE, MONTHLY, YEARLY, CONSECUTIVE }

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
    val recurrence: ReminderRecurrence = ReminderRecurrence.Once,
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
    fun editRecurrence(value: ReminderRecurrence) = copy(recurrence = value, notice = null)
    fun selectRecurrenceMode(value: ReminderRecurrence, consecutive: Boolean) = if (consecutive) {
        copy(recurrence = ReminderRecurrence.Once, activeDayRule = ReminderActiveDayRule.ConsecutiveDateRange, notice = null)
    } else {
        copy(recurrence = value, activeDayRule = ReminderActiveDayRule.Daily, notice = null)
    }
    fun editProfile(value: ReminderProfileSnapshot?) = copy(
        profile = value?.copy(),
        daytimeBackoffInput = value?.daytimeDismissalBackoffMinutes?.joinToString(",").orEmpty(),
        eveningIntervalInput = value?.eveningIntervalMinutes?.toString().orEmpty(),
        notice = null,
    )
    fun updateProfile(value: ReminderProfileSnapshot?) = copy(profile = value?.copy(), notice = null)
    fun selectProfile(value: ReminderProfile): ReminderDraftUiState {
        val selected = if (value.kind == ReminderProfileKind.CUSTOM) {
            value.snapshot.copy()
        } else {
            value.snapshot.withGlobalScheduleRulesFrom(profile)
        }
        return copy(
            profile = selected,
            profileId = value.id,
            daytimeBackoffInput = selected.daytimeDismissalBackoffMinutes.joinToString(","),
            eveningIntervalInput = selected.eveningIntervalMinutes?.toString().orEmpty(),
            notice = null,
        )
    }
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
            draft = ReminderDraft(id, content.trim(), startDate, endDate, firstReminderTime, activeDayRule, profile?.copy(), recurrence = recurrence),
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
            recurrence = draft.recurrence,
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

private fun ReminderProfileSnapshot.withGlobalScheduleRulesFrom(current: ReminderProfileSnapshot?): ReminderProfileSnapshot {
    current ?: return copy()
    return copy(
        sleepStart = current.sleepStart,
        sleepEnd = current.sleepEnd,
        workDays = current.workDays,
        workStart = current.workStart,
        workEnd = current.workEnd,
        dailyCutoff = current.dailyCutoff,
    )
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

fun canResumeReminder(reminder: Reminder): Boolean = reminder.dataIssue == null

data class ReminderUiState(
    val drafts: Map<String, ReminderDraftUiState> = emptyMap(),
    val filter: ReminderFilter = ReminderFilter.ACTIVE,
    val listMode: ReminderListMode = ReminderListMode.RECENT,
    val listFilter: ReminderListFilter = ReminderListFilter(),
    val isListSearchVisible: Boolean = false,
    val selectedReminderId: String? = null,
    val aiParse: ReminderAiParseState = ReminderAiParseState(),
    val error: String? = null,
)

class ReminderViewModel(
    private val repository: ReminderRepository,
    private val coordinator: ReminderCoordinator,
    private val settingRepository: SettingRepository,
    private val textInterpreter: ReminderTextInterpreter,
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
    val listState: StateFlow<ReminderListState> = combine(reminders, state) { items, ui ->
        buildReminderListState(items, Clock.System.todayIn(TimeZone.currentSystemDefault()), ui.listMode, ui.listFilter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), buildReminderListState(emptyList(), Clock.System.todayIn(TimeZone.currentSystemDefault()), ReminderListMode.RECENT, ReminderListFilter()))

    fun registerDraft(draft: ReminderDraft) {
        _state.update { current ->
            if (draft.id in current.drafts) current else {
                val default = defaultProfile()
                current.copy(drafts = current.drafts + (draft.id to ReminderDraftUiState.from(draft, default.snapshot, default.id)))
            }
        }
    }

    fun onAiPromptChanged(value: String) {
        _state.update { it.copy(aiParse = it.aiParse.onPromptChanged(value)) }
    }

    fun interpretAiPrompt() {
        val prompt = state.value.aiParse.prompt
        _state.update { it.copy(aiParse = it.aiParse.onExplicitSubmit()) }
        viewModelScope.launch(Dispatchers.IO) {
            val interpretation = textInterpreter.interpret(prompt, Clock.System.now(), TimeZone.currentSystemDefault())
            _state.update { current ->
                val next = if (interpretation.failure == null) {
                    val default = defaultProfile()
                    current.copy(drafts = current.drafts + (interpretation.draft.id to ReminderDraftUiState.from(interpretation.draft, default.snapshot, default.id)))
                } else current
                next.copy(aiParse = next.aiParse.onInterpretationFinished(interpretation.failure))
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

    fun setListMode(mode: ReminderListMode) {
        _state.update { it.copy(listMode = mode) }
    }

    fun updateListFilter(transform: (ReminderListFilter) -> ReminderListFilter) {
        _state.update { it.copy(listFilter = transform(it.listFilter)) }
    }

    fun toggleListSearch() {
        _state.update { it.copy(isListSearchVisible = !it.isListSearchVisible) }
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

    fun applyLatestProfile(id: String) = applyLatestProfile(id, defaultProfile().snapshot)

    fun delete(id: String) = mutateAndRecompute(id) { repository.delete(id) }

    fun saveEditor(existing: Reminder?, editor: ReminderEditorState, onResult: (String?, ReminderEditorState) -> Unit) {
        if (!editor.canSave) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val id = existing?.id ?: UUID.randomUUID().toString()
                    if (existing == null) {
                        repository.createConfirmed(
                            ReminderDraft(id, editor.content.trim(), editor.startDate, editor.endDate, editor.firstReminderTime, editor.activeDayRule, editor.profile, recurrence = editor.recurrence),
                            editor.profile,
                        )
                    } else {
                        check(repository.update(id, ReminderEdit(existing.version, editor.content.trim(), editor.startDate, editor.endDate, editor.firstReminderTime, editor.activeDayRule, editor.recurrence, editor.profile)))
                    }
                    coordinator.recompute(id)
                    id
                }
            }
            result.fold(
                onSuccess = { onResult(it, editor.copy(saving = false)) },
                onFailure = { onResult(null, editor.copy(saving = false, notice = "\u4fdd\u5b58\u6216\u8c03\u5ea6\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5\u3002")) },
            )
        }
    }

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

package com.dailysatori.ui.feature.reminder

import com.dailysatori.service.reminder.LeapDayPolicy
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderImportance
import com.dailysatori.service.reminder.ReminderLockScreenVisibility
import com.dailysatori.service.reminder.ReminderProfileKind
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderRecurrence
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val reminderBatchJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
private data class SavedBatchState(
    val prompt: String,
    val submitCount: Int,
    val requestToken: Long,
    val generation: Long,
    val error: String?,
    val batchId: String?,
    val failure: String?,
    val items: List<SavedBatchItem>,
)

@Serializable
private data class SavedBatchItem(
    val id: String, val sourceText: String, val draft: SavedDraft, val parseError: String?,
    val requiresConfirmation: Boolean, val selected: Boolean, val status: String,
    val createdReminderId: String?, val saveError: String?,
)

@Serializable
private data class SavedDraft(
    val id: String, val content: String, val startDate: String?, val endDate: String?, val firstTime: String?,
    val activeRule: String, val activeDays: List<String>, val recurrence: String,
    val recurrenceDay: Int?, val recurrenceMonth: Int?, val leapPolicy: String?,
    val profile: SavedProfile?, val profileId: String?, val backoffInput: String, val eveningInput: String,
)

@Serializable
private data class SavedProfile(
    val kind: String, val backoff: List<Int>, val eveningStart: String, val eveningInterval: Int?,
    val eveningTimes: List<String>, val cutoff: String, val sound: Boolean, val vibration: Boolean,
    val sleepStart: String, val sleepEnd: String, val workDays: List<String>, val workStart: String,
    val workEnd: String, val importance: String, val visibility: String,
)

internal fun encodeReminderBatchState(state: ReminderAiParseState): String = reminderBatchJson.encodeToString(
    SavedBatchState(
        prompt = state.prompt, submitCount = state.submitCount, requestToken = state.requestToken,
        generation = state.batchGeneration, error = state.error, batchId = state.batch?.batchId,
        failure = state.batch?.failure, items = state.batch?.items?.values?.map { it.toSaved() }.orEmpty(),
    ),
)

internal fun decodeReminderBatchState(value: String?): ReminderAiParseState? = value?.let {
    runCatching { reminderBatchJson.decodeFromString<SavedBatchState>(it).toUi() }.getOrNull()
}

private fun ReminderBatchUiItem.toSaved() = SavedBatchItem(
    id, sourceText, draft.toSaved(), parseError, requiresConfirmation, selected, saveStatus.name,
    createdReminderId, saveError,
)

internal fun encodeReminderBatchItem(item: ReminderBatchUiItem): String =
    reminderBatchJson.encodeToString(item.toSaved())

internal fun decodeReminderBatchItem(value: String): ReminderBatchUiItem? =
    runCatching { reminderBatchJson.decodeFromString<SavedBatchItem>(value).toUiItem() }.getOrNull()

private fun SavedBatchItem.toUiItem(): ReminderBatchUiItem {
    val interrupted = status == BatchSaveStatus.SAVING.name
    return ReminderBatchUiItem(
        id = id, sourceText = sourceText, draft = draft.toUi(), parseError = parseError,
        requiresConfirmation = requiresConfirmation, selected = if (interrupted) true else selected,
        saveStatus = if (interrupted) BatchSaveStatus.FAILED else enumValueOf(status),
        createdReminderId = createdReminderId,
        saveError = if (interrupted) ReminderBatchErrorCode.SAVE_FAILED else saveError,
    )
}

private fun ReminderDraftUiState.toSaved(): SavedDraft {
    val rule = activeDayRule
    val repeat = recurrence
    return SavedDraft(
        id, content, startDate?.toString(), endDate?.toString(), firstReminderTime?.toString(),
        activeRule = when (rule) {
            ReminderActiveDayRule.Daily -> "daily"
            ReminderActiveDayRule.Weekdays -> "weekdays"
            ReminderActiveDayRule.ConsecutiveDateRange -> "range"
            is ReminderActiveDayRule.SelectedWeekdays -> "selected"
        },
        activeDays = (rule as? ReminderActiveDayRule.SelectedWeekdays)?.days?.map { it.name }.orEmpty(),
        recurrence = when (repeat) {
            ReminderRecurrence.Once -> "once"
            is ReminderRecurrence.Monthly -> "monthly"
            is ReminderRecurrence.Yearly -> "yearly"
        },
        recurrenceDay = when (repeat) { is ReminderRecurrence.Monthly -> repeat.dayOfMonth; is ReminderRecurrence.Yearly -> repeat.dayOfMonth; else -> null },
        recurrenceMonth = (repeat as? ReminderRecurrence.Yearly)?.month,
        leapPolicy = (repeat as? ReminderRecurrence.Yearly)?.leapDayPolicy?.name,
        profile = profile?.toSaved(), profileId = profileId,
        backoffInput = daytimeBackoffInput, eveningInput = eveningIntervalInput,
    )
}

private fun ReminderProfileSnapshot.toSaved() = SavedProfile(
    kind.name, daytimeDismissalBackoffMinutes, eveningStart.toString(), eveningIntervalMinutes,
    eveningTimes.map { it.toString() }, dailyCutoff.toString(), soundEnabled, vibrationEnabled,
    sleepStart.toString(), sleepEnd.toString(), workDays.map { it.name }, workStart.toString(),
    workEnd.toString(), importance.name, lockScreenVisibility.name,
)

private fun SavedBatchState.toUi(): ReminderAiParseState {
    val restoredItems = items.associateTo(linkedMapOf()) { saved ->
        val interrupted = saved.status == BatchSaveStatus.SAVING.name
        saved.id to ReminderBatchUiItem(
            id = saved.id, sourceText = saved.sourceText, draft = saved.draft.toUi(), parseError = saved.parseError,
            requiresConfirmation = saved.requiresConfirmation, selected = if (interrupted) true else saved.selected,
            saveStatus = if (interrupted) BatchSaveStatus.FAILED else enumValueOf(saved.status),
            createdReminderId = saved.createdReminderId,
            saveError = if (interrupted) ReminderBatchErrorCode.SAVE_FAILED else saved.saveError,
        )
    }
    return ReminderAiParseState(
        prompt = prompt, isInterpreting = false, submitCount = submitCount, requestToken = requestToken + 1,
        batchGeneration = generation, error = error,
        batch = batchId?.let { ReminderBatchUiState(it, restoredItems, failure) },
    )
}

private fun SavedDraft.toUi() = ReminderDraftUiState(
    id = id, content = content, startDate = startDate?.let(LocalDate::parse), endDate = endDate?.let(LocalDate::parse),
    firstReminderTime = firstTime?.let(LocalTime::parse), activeDayRule = when (activeRule) {
        "weekdays" -> ReminderActiveDayRule.Weekdays
        "range" -> ReminderActiveDayRule.ConsecutiveDateRange
        "selected" -> ReminderActiveDayRule.SelectedWeekdays(activeDays.mapTo(linkedSetOf(), DayOfWeek::valueOf))
        else -> ReminderActiveDayRule.Daily
    },
    recurrence = when (recurrence) {
        "monthly" -> ReminderRecurrence.Monthly(requireNotNull(recurrenceDay))
        "yearly" -> ReminderRecurrence.Yearly(requireNotNull(recurrenceMonth), requireNotNull(recurrenceDay), LeapDayPolicy.valueOf(requireNotNull(leapPolicy)))
        else -> ReminderRecurrence.Once
    },
    profile = profile?.toUi(), profileId = profileId, daytimeBackoffInput = backoffInput,
    eveningIntervalInput = eveningInput,
)

private fun SavedProfile.toUi() = ReminderProfileSnapshot(
    kind = ReminderProfileKind.valueOf(kind), daytimeDismissalBackoffMinutes = backoff,
    eveningStart = LocalTime.parse(eveningStart), eveningIntervalMinutes = eveningInterval,
    eveningTimes = eveningTimes.mapTo(linkedSetOf(), LocalTime::parse), dailyCutoff = LocalTime.parse(cutoff),
    soundEnabled = sound, vibrationEnabled = vibration, sleepStart = LocalTime.parse(sleepStart),
    sleepEnd = LocalTime.parse(sleepEnd), workDays = workDays.mapTo(linkedSetOf(), DayOfWeek::valueOf),
    workStart = LocalTime.parse(workStart), workEnd = LocalTime.parse(workEnd),
    importance = ReminderImportance.valueOf(importance),
    lockScreenVisibility = ReminderLockScreenVisibility.valueOf(visibility),
)

package com.dailysatori.ui.feature.settings.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.app.NotificationManagerCompat
import com.dailysatori.data.repository.ReminderProfile
import com.dailysatori.data.repository.ReminderRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.reminder.ReminderProfileKind
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderImportance
import com.dailysatori.service.reminder.ReminderLockScreenVisibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import java.util.UUID

data class ReminderDeliveryAccess(
    val notificationsAllowed: Boolean,
    val exactAlarmsAllowed: Boolean,
    val disabledChannelIds: Set<String> = emptySet(),
) {
    val usesFallbackTiming: Boolean get() = !exactAlarmsAllowed
}

fun interface ReminderDeliveryAccessChecker {
    fun current(): ReminderDeliveryAccess
}

class ReminderDeliveryAccessController(private val checker: ReminderDeliveryAccessChecker) {
    var current: ReminderDeliveryAccess = checker.current()
        private set

    fun refresh(): ReminderDeliveryAccess = checker.current().also { current = it }
}

class AndroidReminderDeliveryAccessChecker(private val context: Context) : ReminderDeliveryAccessChecker {
    override fun current(): ReminderDeliveryAccess {
        val runtimePermission = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val notificationsAllowed = runtimePermission && NotificationManagerCompat.from(context).areNotificationsEnabled()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        val manager = context.getSystemService(NotificationManager::class.java)
        val disabled = ReminderSettingsViewModel.CHANNEL_IDS.filterTo(mutableSetOf()) { id ->
            Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(id)?.importance == NotificationManager.IMPORTANCE_NONE
        }
        return ReminderDeliveryAccess(notificationsAllowed, exactAllowed, disabled)
    }
}

data class ReminderSettingsState(
    val profiles: List<ReminderProfile>,
    val defaultProfileId: String,
    val sleepStart: LocalTime,
    val sleepEnd: LocalTime,
    val workDays: Set<DayOfWeek>,
    val workStart: LocalTime,
    val workEnd: LocalTime,
    val defaultSoundEnabled: Boolean = true,
    val defaultVibrationEnabled: Boolean = true,
    val defaultImportance: ReminderImportance = ReminderImportance.HIGH,
    val defaultLockScreenVisibility: ReminderLockScreenVisibility = ReminderLockScreenVisibility.PRIVATE,
    val deliveryAccess: ReminderDeliveryAccess = ReminderDeliveryAccess(true, true),
    val editor: ReminderProfileEditorState? = null,
    val saving: Boolean = false,
) {
    companion object {
        const val STRONG_ID = "builtin-strong"
        const val STANDARD_ID = "builtin-standard"
        const val GENTLE_ID = "builtin-gentle"

        fun defaults() = ReminderSettingsState(
            profiles = builtInProfiles(),
            defaultProfileId = STANDARD_ID,
            sleepStart = LocalTime(0, 0),
            sleepEnd = LocalTime(9, 0),
            workDays = weekdays,
            workStart = LocalTime(9, 0),
            workEnd = LocalTime(18, 0),
        )

        fun builtInProfiles() = listOf(
            ReminderProfile(STRONG_ID, "Strong", ReminderProfileKind.STRONG, ReminderProfileSnapshot.strong()),
            ReminderProfile(STANDARD_ID, "Standard", ReminderProfileKind.STANDARD, ReminderProfileSnapshot.standard()),
            ReminderProfile(GENTLE_ID, "Gentle", ReminderProfileKind.GENTLE, ReminderProfileSnapshot.gentle()),
        )

        val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    }
}

data class ReminderProfileEditorState(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: ReminderProfileKind = ReminderProfileKind.CUSTOM,
    val daytimeBackoffMinutes: List<Int>,
    val eveningStart: LocalTime,
    val eveningIntervalMinutes: Int?,
    val dailyCutoff: LocalTime,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val daytimeBackoffInput: String = daytimeBackoffMinutes.joinToString(","),
    val importance: ReminderImportance = ReminderImportance.HIGH,
    val lockScreenVisibility: ReminderLockScreenVisibility = ReminderLockScreenVisibility.PRIVATE,
) {
    val isValid: Boolean get() = name.isNotBlank() && name.length <= 80 &&
        parseReminderBackoff(daytimeBackoffInput) != null &&
        (eveningIntervalMinutes == null || eveningIntervalMinutes in 1..1_440)

    fun editBackoffInput(value: String): ReminderProfileEditorState = copy(
        daytimeBackoffInput = value,
        daytimeBackoffMinutes = parseReminderBackoff(value) ?: daytimeBackoffMinutes,
    )

    fun toProfile(settings: ReminderSettingsState): ReminderProfile? {
        if (!isValid) return null
        val snapshot = ReminderProfileSnapshot(
            kind = kind,
            daytimeDismissalBackoffMinutes = daytimeBackoffMinutes,
            eveningStart = eveningStart,
            eveningIntervalMinutes = eveningIntervalMinutes,
            dailyCutoff = dailyCutoff,
            soundEnabled = soundEnabled,
            vibrationEnabled = vibrationEnabled,
            sleepStart = settings.sleepStart,
            sleepEnd = settings.sleepEnd,
            workDays = settings.workDays,
            workStart = settings.workStart,
            workEnd = settings.workEnd,
            importance = importance,
            lockScreenVisibility = lockScreenVisibility,
        )
        return ReminderProfile(id, name.trim(), kind, snapshot)
    }

    companion object {
        fun from(profile: ReminderProfile, duplicate: Boolean = false) = ReminderProfileEditorState(
            id = if (duplicate) UUID.randomUUID().toString() else profile.id,
            name = profile.name,
            kind = ReminderProfileKind.CUSTOM,
            daytimeBackoffMinutes = profile.snapshot.daytimeDismissalBackoffMinutes,
            eveningStart = profile.snapshot.eveningStart,
            eveningIntervalMinutes = profile.snapshot.eveningIntervalMinutes,
            dailyCutoff = profile.snapshot.dailyCutoff,
            soundEnabled = profile.snapshot.soundEnabled,
            vibrationEnabled = profile.snapshot.vibrationEnabled,
            daytimeBackoffInput = profile.snapshot.daytimeDismissalBackoffMinutes.joinToString(","),
            importance = profile.snapshot.importance,
            lockScreenVisibility = profile.snapshot.lockScreenVisibility,
        )
    }
}

class ReminderSettingsViewModel(
    private val repository: ReminderRepository,
    private val settingRepository: SettingRepository,
    private val context: Context,
    private val deliveryAccessController: ReminderDeliveryAccessController,
) : ViewModel() {
    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<ReminderSettingsState> = _state

    init {
        refreshDeliveryAccess()
        viewModelScope.launch(Dispatchers.IO) {
            repository.observeProfiles().collect { custom ->
                _state.update { it.copy(profiles = ReminderSettingsState.builtInProfiles() + custom) }
            }
        }
    }

    fun setDefaultProfile(id: String) {
        if (_state.value.profiles.none { it.id == id }) return
        settingRepository.upsert(KEY_DEFAULT_PROFILE, id)
        _state.update { it.copy(defaultProfileId = id) }
    }

    fun setQuietHours(start: LocalTime, end: LocalTime) {
        settingRepository.upsert(KEY_SLEEP_START, start.toString())
        settingRepository.upsert(KEY_SLEEP_END, end.toString())
        _state.update { it.copy(sleepStart = start, sleepEnd = end) }
    }

    fun setWorkHours(days: Set<DayOfWeek>, start: LocalTime, end: LocalTime) {
        settingRepository.upsert(KEY_WORK_DAYS, days.joinToString(",") { it.name })
        settingRepository.upsert(KEY_WORK_START, start.toString())
        settingRepository.upsert(KEY_WORK_END, end.toString())
        _state.update { it.copy(workDays = days, workStart = start, workEnd = end) }
    }

    fun setDefaultDelivery(sound: Boolean, vibration: Boolean, importance: ReminderImportance, visibility: ReminderLockScreenVisibility) {
        settingRepository.upsert(KEY_SOUND, sound.toString())
        settingRepository.upsert(KEY_VIBRATION, vibration.toString())
        settingRepository.upsert(KEY_IMPORTANCE, importance.name)
        settingRepository.upsert(KEY_VISIBILITY, visibility.name)
        _state.update { it.copy(defaultSoundEnabled = sound, defaultVibrationEnabled = vibration, defaultImportance = importance, defaultLockScreenVisibility = visibility) }
    }

    fun editProfile(profile: ReminderProfile? = null, duplicate: Boolean = false, displayName: String? = null) {
        _state.update {
            it.copy(editor = profile?.let { value -> ReminderProfileEditorState.from(value, duplicate).let { editor -> displayName?.let { editor.copy(name = it) } ?: editor } } ?: ReminderProfileEditorState(
                name = "", daytimeBackoffMinutes = listOf(120, 240), eveningStart = LocalTime(22, 0), eveningIntervalMinutes = 60,
                dailyCutoff = LocalTime(0, 0), soundEnabled = true, vibrationEnabled = true,
            ))
        }
    }

    fun updateEditor(editor: ReminderProfileEditorState) = _state.update { it.copy(editor = editor) }
    fun dismissEditor() = _state.update { it.copy(editor = null) }

    fun saveEditor() {
        val profile = _state.value.editor?.toProfile(_state.value) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.upsertProfile(profile)
            _state.update { it.copy(editor = null) }
        }
    }

    fun deleteProfile(profile: ReminderProfile) {
        if (profile.kind != ReminderProfileKind.CUSTOM) return
        viewModelScope.launch(Dispatchers.IO) {
            if (repository.deleteProfile(profile.id) && _state.value.defaultProfileId == profile.id) setDefaultProfile(ReminderSettingsState.STANDARD_ID)
        }
    }

    fun refreshDeliveryAccess() {
        _state.update { it.copy(deliveryAccess = deliveryAccessController.refresh()) }
    }

    fun openNotificationSettings() = openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

    fun openExactAlarmSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openChannelSettings(channelId: String) = openSettings(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS) {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
    }

    private fun openSettings(action: String, configure: Intent.() -> Unit) {
        context.startActivity(Intent(action).apply(configure).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun loadState(): ReminderSettingsState {
        val defaults = ReminderSettingsState.defaults()
        fun time(key: String, fallback: LocalTime) = settingRepository.get(key)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: fallback
        val days = settingRepository.get(KEY_WORK_DAYS)?.split(',')?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }?.toSet().orEmpty().ifEmpty { defaults.workDays }
        return defaults.copy(
            profiles = defaults.profiles + repository.profiles(),
            defaultProfileId = settingRepository.get(KEY_DEFAULT_PROFILE) ?: defaults.defaultProfileId,
            sleepStart = time(KEY_SLEEP_START, defaults.sleepStart), sleepEnd = time(KEY_SLEEP_END, defaults.sleepEnd),
            workDays = days, workStart = time(KEY_WORK_START, defaults.workStart), workEnd = time(KEY_WORK_END, defaults.workEnd),
            defaultSoundEnabled = settingRepository.get(KEY_SOUND)?.toBooleanStrictOrNull() ?: defaults.defaultSoundEnabled,
            defaultVibrationEnabled = settingRepository.get(KEY_VIBRATION)?.toBooleanStrictOrNull() ?: defaults.defaultVibrationEnabled,
            defaultImportance = settingRepository.get(KEY_IMPORTANCE)?.let { runCatching { ReminderImportance.valueOf(it) }.getOrNull() } ?: defaults.defaultImportance,
            defaultLockScreenVisibility = settingRepository.get(KEY_VISIBILITY)?.let { runCatching { ReminderLockScreenVisibility.valueOf(it) }.getOrNull() } ?: defaults.defaultLockScreenVisibility,
        )
    }

    companion object {
        val CHANNEL_IDS = listOf("reminder-sound-vibration-v1", "reminder-sound-v1", "reminder-vibration-v1", "reminder-silent-v1")
            .flatMap { id -> listOf(id, "$id-default", "$id-low") }
        const val KEY_DEFAULT_PROFILE = "reminder.default_profile"
        const val KEY_SLEEP_START = "reminder.sleep_start"
        const val KEY_SLEEP_END = "reminder.sleep_end"
        const val KEY_WORK_DAYS = "reminder.work_days"
        const val KEY_WORK_START = "reminder.work_start"
        const val KEY_WORK_END = "reminder.work_end"
        const val KEY_SOUND = "reminder.sound"
        const val KEY_VIBRATION = "reminder.vibration"
        const val KEY_IMPORTANCE = "reminder.importance"
        const val KEY_VISIBILITY = "reminder.visibility"
    }
}

fun parseReminderBackoff(value: String): List<Int>? {
    if (value.isBlank() || value.startsWith(',') || value.endsWith(',') || ",," in value) return null
    val tokens = value.split(',').map { it.trim() }
    if (tokens.size > 8 || tokens.any { it.isEmpty() }) return null
    return tokens.map { it.toIntOrNull() ?: return null }.takeIf { values -> values.all { it in 1..1_440 } }
}

package com.dailysatori.core.diagnostics

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.dailysatori.config.SettingKeys
import com.dailysatori.core.service.InstalledBuild
import com.dailysatori.core.service.UpdateChannel
import kotlinx.coroutines.CancellationException

data class RecoveryUpdateConfiguration(val channel: UpdateChannel, val schemaVersion: Long, val usedDefaults: Boolean = false)

internal fun resolveRecoveryUpdateConfiguration(
    settings: Map<String, String?>,
    installed: InstalledBuild,
    unavailable: Boolean = false,
): RecoveryUpdateConfiguration {
    val channel = UpdateChannel.entries.firstOrNull { it.id == settings["update_channel"] }
    val schema = settings[SettingKeys.schemaVersion]?.toLongOrNull()?.takeIf { it > 0 }
    return RecoveryUpdateConfiguration(channel ?: installed.channel, maxOf(installed.schemaVersion, schema ?: 0),
        unavailable || (settings["update_channel"] != null && channel == null) ||
            (settings[SettingKeys.schemaVersion] != null && schema == null))
}

/** No SQLDelight driver, migrations, secrets or business services are opened in recovery. */
internal fun readRecoveryUpdateConfiguration(context: Context, installed: InstalledBuild): RecoveryUpdateConfiguration {
    val file = context.getDatabasePath("daily_satori.db")
    if (!file.isFile) return resolveRecoveryUpdateConfiguration(emptyMap(), installed)
    return try {
        val values = SQLiteDatabase.openDatabase(file.path, null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { database ->
            database.rawQuery("SELECT key, value FROM setting WHERE key IN (?, ?)",
                arrayOf("update_channel", SettingKeys.schemaVersion)).use { cursor ->
                buildMap<String, String?> {
                    while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1))
                }
            }
        }
        resolveRecoveryUpdateConfiguration(values, installed)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { resolveRecoveryUpdateConfiguration(emptyMap(), installed, unavailable = true) }
}

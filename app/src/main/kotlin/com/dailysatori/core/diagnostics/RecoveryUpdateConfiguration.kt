package com.dailysatori.core.diagnostics

import android.content.Context
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

/** No SQLDelight driver, migrations or business services are opened in recovery. */
internal fun readRecoveryUpdateConfiguration(context: Context, installed: InstalledBuild): RecoveryUpdateConfiguration =
    loadRecoveryUpdateConfiguration(installed) {
        com.dailysatori.platform.readRecoverySettings(com.dailysatori.platform.PlatformContext(context))
    }

internal fun loadRecoveryUpdateConfiguration(installed: InstalledBuild,
    readSettings: () -> Map<String, String?>): RecoveryUpdateConfiguration = try {
    resolveRecoveryUpdateConfiguration(readSettings(), installed)
} catch (cancelled: CancellationException) { throw cancelled }
catch (_: Exception) { resolveRecoveryUpdateConfiguration(emptyMap(), installed, unavailable = true) }
catch (_: LinkageError) { resolveRecoveryUpdateConfiguration(emptyMap(), installed, unavailable = true) }

package com.dailysatori.ui.component.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dailysatori.service.i18n.I18nService
import org.koin.compose.koinInject

internal enum class SmsAccessAction { ENABLE, REQUEST, EXPLAIN, SETTINGS }

internal fun smsAccessAction(granted: Boolean, requested: Boolean, rationale: Boolean): SmsAccessAction = when {
    granted -> SmsAccessAction.ENABLE
    rationale -> SmsAccessAction.EXPLAIN
    requested -> SmsAccessAction.SETTINGS
    else -> SmsAccessAction.REQUEST
}

/** Retains the user's enable intent across the system permission/settings round trip. */
@Composable
internal fun rememberSmsPermissionAccess(busy: Boolean, onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val i18n: I18nService = koinInject()
    val preferences = remember(context) { context.getSharedPreferences("sms_permission_access", Context.MODE_PRIVATE) }
    val enable by rememberUpdatedState(onGranted)
    var awaitingGrant by rememberSaveable { mutableStateOf(false) }
    var granted by remember { mutableStateOf(context.hasSmsAccess()) }
    var explanation by rememberSaveable { mutableStateOf<SmsAccessAction?>(null) }
    var settingsFailed by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        awaitingGrant = awaitingGrant && allowed
        if (!allowed) {
            awaitingGrant = false
            explanation = SmsAccessAction.SETTINGS
        }
    }
    val appSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        granted = context.hasSmsAccess()
        awaitingGrant = awaitingGrant && granted
    }
    val launchRequest = {
        preferences.edit().putBoolean("requested", true).apply()
        awaitingGrant = true
        if (runCatching { request.launch(Manifest.permission.RECEIVE_SMS) }.isFailure) {
            awaitingGrant = false
            explanation = SmsAccessAction.SETTINGS
        }
    }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = context.hasSmsAccess()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(granted, awaitingGrant, busy) {
        if (granted && awaitingGrant && !busy) {
            awaitingGrant = false
            enable()
        }
    }
    explanation?.let { action ->
        AlertDialog(
            onDismissRequest = { explanation = null },
            title = { Text(i18n.t("phone.sms_access_title")) },
            text = { Text(i18n.t(if (action == SmsAccessAction.EXPLAIN) "phone.sms_access_reason" else "phone.sms_access_settings"),
                Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = {
                    explanation = null
                    if (action == SmsAccessAction.EXPLAIN) launchRequest()
                    else {
                        awaitingGrant = true
                        if (runCatching { appSettings.launch(appPermissionSettingsIntent(context)) }.isFailure) {
                            awaitingGrant = false
                            settingsFailed = true
                        }
                    }
                }) { Text(i18n.t(if (action == SmsAccessAction.EXPLAIN) "phone.continue_access" else "phone.open_app_settings")) }
            },
            dismissButton = { TextButton(onClick = { explanation = null }) { Text(i18n.t("bookkeeping.cancel")) } },
        )
    }
    if (settingsFailed) AlertDialog(onDismissRequest = { settingsFailed = false },
        title = { Text(i18n.t("phone.open_app_settings")) }, text = { Text(i18n.t("phone.settings_unavailable")) },
        confirmButton = { TextButton(onClick = { settingsFailed = false }) { Text(i18n.t("bookkeeping.done")) } })
    return {
        granted = context.hasSmsAccess()
        val activity = context.permissionActivity()
        val rationale = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECEIVE_SMS) } ?: false
        when (smsAccessAction(granted, preferences.getBoolean("requested", false), rationale)) {
            SmsAccessAction.ENABLE -> awaitingGrant = true
            SmsAccessAction.REQUEST -> launchRequest()
            SmsAccessAction.EXPLAIN -> explanation = SmsAccessAction.EXPLAIN
            SmsAccessAction.SETTINGS -> explanation = SmsAccessAction.SETTINGS
        }
    }
}

internal fun openAppPermissionSettings(context: Context): Boolean = runCatching {
    context.startActivity(appPermissionSettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess

private fun appPermissionSettingsIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

private fun Context.hasSmsAccess() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

private fun Context.permissionActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.permissionActivity()
    else -> null
}

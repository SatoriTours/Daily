package com.dailysatori.core.diagnostics

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.dailysatori.BuildConfig
import com.dailysatori.service.diagnostics.*
import java.io.File
import okhttp3.OkHttpClient

object DiagnosticRuntime {
    lateinit var store: DiagnosticStore
        private set
    @Volatile var exitInfoCoverage: String = "not-read"
        private set

    @Synchronized
    fun initialize(application: Application) {
        if (::store.isInitialized) return
        store = DiagnosticStore(File(application.noBackupFilesDir, "diagnostics"), previousExit = { lastSeen ->
            try {
                DiagnosticExitInfoReader.read(application, lastSeen).also {
                    exitInfoCoverage = when {
                        Build.VERSION.SDK_INT < 30 -> "unsupported-api"
                        it == null -> "no-new-exit"
                        else -> "reason-only; raw ANR/native traces not collected"
                    }
                }
            } catch (_: Exception) { exitInfoCoverage = "unavailable"; null }
        })
        DiagnosticLog.registerCoverage(DiagnosticSource.APP, DiagnosticCoverage.LIFECYCLE_ONLY)
        val diagnostics = Diagnostics(store)
        DiagnosticLog.diagnostics = diagnostics
        // Existing free-text logcat output may contain private API responses too.
        Logger.setLogWriters(DiagnosticLogWriter(diagnostics))
        Logger.setMinSeverity(Severity.Debug)
        val previous = Thread.getDefaultUncaughtExceptionHandler() ?: Thread.UncaughtExceptionHandler { _, _ ->
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(1)
        }
        Thread.setDefaultUncaughtExceptionHandler(DiagnosticCrashHandler(store.root, previous))
        diagnostics.emit(DiagnosticCode.APP_START, DiagnosticSource.APP,
            fields = mapOf("sdk" to Build.VERSION.SDK_INT.toString(), "versionCode" to BuildConfig.VERSION_CODE.toString()))
        installImages()
        installLifecycle(application)
        installNetwork(application)
    }

    private fun installImages() {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context).components {
                add(OkHttpNetworkFetcherFactory(callFactory = {
                    OkHttpClient.Builder().eventListenerFactory(diagnosticEventListenerFactory(DiagnosticSource.IMAGE)).build()
                }))
            }.eventListener(object : coil3.EventListener() {
                override fun onError(request: coil3.request.ImageRequest, result: coil3.request.ErrorResult) {
                    DiagnosticLog.diagnostics.emit(DiagnosticCode.OPERATION_FAILED, DiagnosticSource.IMAGE,
                        DiagnosticLevel.WARNING, error = result.throwable)
                }
            }).build()
        }
    }

    private fun installLifecycle(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0
            private var changingConfiguration = false
            override fun onActivityStarted(activity: Activity) {
                if (started++ == 0 && !changingConfiguration) {
                    DiagnosticLog.diagnostics.emit(DiagnosticCode.FOREGROUND, DiagnosticSource.APP, fields = mapOf(
                        "permission.microphone" to (application.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED).toString(),
                        "permission.notifications" to application.getSystemService(NotificationManager::class.java).areNotificationsEnabled().toString(),
                    ))
                }
                changingConfiguration = false
            }
            override fun onActivityStopped(activity: Activity) {
                changingConfiguration = activity.isChangingConfigurations
                if (--started == 0 && !changingConfiguration) {
                    DiagnosticLog.diagnostics.emit(DiagnosticCode.BACKGROUND, DiagnosticSource.APP)
                }
            }
            override fun onActivityResumed(activity: Activity) {
                DiagnosticLog.diagnostics.emit(DiagnosticCode.PAGE_VIEW, DiagnosticSource.APP,
                    fields = mapOf("screen" to activity.javaClass.simpleName))
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun installNetwork(context: Context) {
        runCatching {
            context.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                private var previousType: String? = null
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val type = when {
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                        else -> "other"
                    }
                    record(type)
                }
                override fun onLost(network: Network) { record("none") }
                private fun record(type: String) {
                    if (type == previousType) return
                    previousType = type
                    DiagnosticLog.diagnostics.emit(DiagnosticCode.NETWORK_CHANGED, DiagnosticSource.APP, fields = mapOf("network" to type))
                }
            })
        }.onFailure { DiagnosticLog.diagnostics.emit(DiagnosticCode.DIAGNOSTIC_GAP, DiagnosticSource.APP, error = it) }
    }
}

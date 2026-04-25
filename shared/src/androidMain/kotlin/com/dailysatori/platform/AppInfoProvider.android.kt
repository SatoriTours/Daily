package com.dailysatori.platform

actual class AppInfoProvider(private val context: PlatformContext) {
    actual fun getAppVersion(): String =
        try {
            context.context.packageManager.getPackageInfo(context.context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) { "unknown" }

    actual fun getAppName(): String =
        context.context.applicationInfo.loadLabel(context.context.packageManager).toString()

    actual fun getPackageName(): String = context.context.packageName
    actual fun isDebugMode(): Boolean = false
}

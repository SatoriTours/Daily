package com.dailysatori.platform

expect class AppInfoProvider {
    fun getAppVersion(): String
    fun getAppName(): String
    fun getPackageName(): String
    fun isDebugMode(): Boolean
}

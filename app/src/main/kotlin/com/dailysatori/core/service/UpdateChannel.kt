package com.dailysatori.core.service

enum class UpdateChannel(val id: String, val label: String) {
    STABLE("stable", "正式版"),
    COMMIT("commit", "提交构建版");

    companion object {
        fun fromId(value: String?): UpdateChannel = entries.firstOrNull { it.id == value } ?: STABLE
    }
}

data class InstalledBuild(val versionCode: Int, val channel: UpdateChannel, val schemaVersion: Long)

data class UpdateCheck(val release: AppRelease? = null, val message: String = "已是所选渠道的最新版本")

internal fun evaluateChannelUpdate(installed: InstalledBuild, target: AppRelease, channel: UpdateChannel): UpdateCheck {
    if (target.channel != channel || target.versionCode == null || target.schemaVersion == null || target.sha256 == null) {
        return UpdateCheck(message = "该渠道暂未提供完整更新信息")
    }
    if (target.versionCode < installed.versionCode) return UpdateCheck(message = "已选择${channel.label}，等待可兼容的新版本")
    if (target.schemaVersion < installed.schemaVersion) return UpdateCheck(message = "当前数据版本较新，等待支持现有数据的${channel.label}")
    val needsUpdate = target.versionCode > installed.versionCode || installed.channel != channel
    return if (needsUpdate) UpdateCheck(release = target, message = "发现可安装的${channel.label}") else UpdateCheck()
}

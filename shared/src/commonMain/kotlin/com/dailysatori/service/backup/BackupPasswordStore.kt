package com.dailysatori.service.backup

import com.dailysatori.platform.PlatformContext

expect class BackupPasswordStore(context: PlatformContext) {
    fun encryptedPassword(password: String): ByteArray
    fun save(password: String)
    fun get(): String?
    fun hasPassword(): Boolean
}

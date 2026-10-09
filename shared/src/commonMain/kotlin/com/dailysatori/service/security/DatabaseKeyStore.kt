package com.dailysatori.service.security

import com.dailysatori.platform.PlatformContext

expect class DatabaseKeyStore(context: PlatformContext) {
    fun readExisting(): DatabaseKey?
    fun wrap(key: DatabaseKey): ByteArray
    fun unwrap(wrapped: ByteArray): DatabaseKey
    fun storagePath(): String
}

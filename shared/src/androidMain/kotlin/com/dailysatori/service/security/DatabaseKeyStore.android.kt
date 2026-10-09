package com.dailysatori.service.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.dailysatori.platform.PlatformContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal actual fun secureDatabaseKeyBytes(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

actual class DatabaseKeyStore internal constructor(
    private val file: File,
    private val loadKey: (Boolean) -> SecretKey,
) {
    actual constructor(context: PlatformContext) : this(
        File(context.context.noBackupFilesDir, "database_key.sec"), ::databaseWrappingKey,
    )

    actual fun storagePath(): String = file.absolutePath

    actual fun readExisting(): DatabaseKey? {
        if (!file.exists()) return null
        return secured { require(file.length() == 93L); unwrap(file.readBytes()) }
    }

    actual fun unwrap(wrapped: ByteArray): DatabaseKey = secured {
        require(wrapped.size == 93 && wrapped[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, loadKey(false), GCMParameterSpec(128, wrapped.copyOfRange(1, 13)))
        val plain = cipher.doFinal(wrapped.copyOfRange(13, wrapped.size))
        try { DatabaseKey.fromHex(plain.toString(Charsets.UTF_8)) } finally { plain.fill(0) }
    }

    /** Returns a staged envelope; never installs it over the active key. */
    actual fun wrap(key: DatabaseKey): ByteArray = secured {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, loadKey(true))
        val plain = key.wrappingValue()
        try { byteArrayOf(1) + cipher.iv + cipher.doFinal(plain) } finally { plain.fill(0) }
    }

    private fun <T> secured(block: () -> T): T = try { block() } catch (_: Exception) { throw DatabaseSecurityException() }
}

private fun databaseWrappingKey(create: Boolean): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey("daily_satori_database_key", null) as? SecretKey)?.let { return it }
    check(create) { "数据库包装密钥缺失" }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(KeyGenParameterSpec.Builder("daily_satori_database_key", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
    return generator.generateKey()
}

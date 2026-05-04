package com.dailysatori.service.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.dailysatori.platform.PlatformContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

actual class BackupPasswordStore actual constructor(context: PlatformContext) {
    private val appContext: Context = context.context.applicationContext
    private val passwordFile: File = File(appContext.filesDir, "backup_password.sec")

    actual fun save(password: String) {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        passwordFile.writeBytes(byteArrayOf(iv.size.toByte()) + iv + encrypted)
    }

    actual fun get(): String? {
        return runCatching {
            if (!passwordFile.exists()) return null
            val bytes = passwordFile.readBytes()
            if (bytes.isEmpty()) return null
            val ivSize = bytes.first().toInt()
            val iv = bytes.copyOfRange(1, 1 + ivSize)
            val encrypted = bytes.copyOfRange(1 + ivSize, bytes.size)
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    actual fun hasPassword(): Boolean = get()?.length.orZero() >= MinBackupPasswordLength

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(Alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            Alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun Int?.orZero(): Int = this ?: 0

    private companion object {
        const val Alias = "daily_satori_backup_password"
        const val Transformation = "AES/GCM/NoPadding"
    }
}

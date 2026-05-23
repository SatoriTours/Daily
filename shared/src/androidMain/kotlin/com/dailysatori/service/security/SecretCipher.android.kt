package com.dailysatori.service.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.dailysatori.platform.PlatformContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

actual class SecretCipher actual constructor(context: PlatformContext) {
    actual fun encrypt(value: String): String {
        if (value.isBlank() || isEncrypted(value)) return value
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return SecretCipherPrefix + encode(cipher.iv) + ":" + encode(encrypted)
    }

    actual fun decrypt(value: String): String {
        if (!isEncrypted(value)) return value
        return runCatching {
            val parts = value.removePrefix(SecretCipherPrefix).split(':', limit = 2)
            if (parts.size != 2) return value
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, decode(parts[0])))
            cipher.doFinal(decode(parts[1])).toString(Charsets.UTF_8)
        }.getOrDefault(value)
    }

    actual fun isEncrypted(value: String): Boolean = value.startsWith(SecretCipherPrefix)

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

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP)

    private companion object {
        const val Alias = "daily_satori_secret_cipher"
        const val Transformation = "AES/GCM/NoPadding"
    }
}

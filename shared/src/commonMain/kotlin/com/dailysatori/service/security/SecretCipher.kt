package com.dailysatori.service.security

import com.dailysatori.platform.PlatformContext

expect class SecretCipher(context: PlatformContext) {
    fun encrypt(value: String): String
    fun decrypt(value: String): String
    fun isEncrypted(value: String): Boolean
}

const val SecretCipherPrefix = "enc:v1:"

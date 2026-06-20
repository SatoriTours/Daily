package com.dailysatori.service.security

import com.dailysatori.platform.PlatformContext

interface SecretValueCipher {
    fun encrypt(value: String): String
    fun decrypt(value: String): String
    fun isEncrypted(value: String): Boolean
}

expect class SecretCipher(context: PlatformContext) : SecretValueCipher {
    override fun encrypt(value: String): String
    override fun decrypt(value: String): String
    override fun isEncrypted(value: String): Boolean
}

const val SecretCipherPrefix = "enc:v1:"

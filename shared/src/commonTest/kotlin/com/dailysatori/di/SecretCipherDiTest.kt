package com.dailysatori.di

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SecretCipherDiTest {
    @Test
    fun sharedModuleExposesSecretCipherThroughItsInterface() {
        val source = File("src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt").readText()

        assertTrue(source.contains("import com.dailysatori.service.security.SecretValueCipher"))
        assertTrue(source.contains("single<SecretValueCipher> { get<SecretCipher>() }"))
    }
}

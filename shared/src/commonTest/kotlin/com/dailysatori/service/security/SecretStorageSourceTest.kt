package com.dailysatori.service.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File

class SecretStorageSourceTest {
    @Test
    fun sensitiveRepositoriesEncryptSecretsBeforeWriting() {
        val ai = File("src/commonMain/kotlin/com/dailysatori/data/repository/AIConfigRepository.kt").readText()
        val mcp = File("src/commonMain/kotlin/com/dailysatori/data/repository/McpServerRepository.kt").readText()
        val remote = File("src/commonMain/kotlin/com/dailysatori/data/repository/RemoteNewsSourceRepository.kt").readText()

        assertTrue(ai.contains("secretCipher.encrypt(apiToken)"))
        assertTrue(mcp.contains("secretCipher.encrypt(apiKey)"))
        assertTrue(remote.contains("secretCipher.encrypt(apiToken.trim())"))
    }

    @Test
    fun sensitiveRepositoriesEncryptExistingPlaintextSecrets() {
        val ai = File("src/commonMain/kotlin/com/dailysatori/data/repository/AIConfigRepository.kt").readText()
        val mcp = File("src/commonMain/kotlin/com/dailysatori/data/repository/McpServerRepository.kt").readText()
        val remote = File("src/commonMain/kotlin/com/dailysatori/data/repository/RemoteNewsSourceRepository.kt").readText()
        val app = File("../app/src/main/kotlin/com/dailysatori/DailySatoriApplication.kt").readText()

        assertTrue(ai.contains("fun encryptStoredSecrets()"))
        assertTrue(mcp.contains("fun encryptStoredSecrets()"))
        assertTrue(remote.contains("fun encryptStoredSecrets()"))
        assertTrue(app.contains("encryptStoredSecrets()"))
        assertTrue(app.contains("AIConfigRepository"))
        assertTrue(app.contains("McpServerRepository"))
        assertTrue(app.contains("RemoteNewsSourceRepository"))
    }

    @Test
    fun sensitiveRepositoriesDecryptSecretsAfterReading() {
        val ai = File("src/commonMain/kotlin/com/dailysatori/data/repository/AIConfigRepository.kt").readText()
        val mcp = File("src/commonMain/kotlin/com/dailysatori/data/repository/McpServerRepository.kt").readText()
        val remote = File("src/commonMain/kotlin/com/dailysatori/data/repository/RemoteNewsSourceRepository.kt").readText()

        assertTrue(ai.contains("decryptConfig"))
        assertTrue(ai.contains("secretCipher.decrypt(config.api_token)"))
        assertTrue(mcp.contains("decryptServer"))
        assertTrue(mcp.contains("secretCipher.decrypt(server.api_key)"))
        assertTrue(remote.contains("decryptSource"))
        assertTrue(remote.contains("secretCipher.decrypt(source.api_token)"))
    }

    @Test
    fun webServerTokenRemainsInSettingRepository() {
        val setting = File("src/commonMain/kotlin/com/dailysatori/data/repository/SettingRepository.kt").readText()

        assertFalse(setting.contains("SecretCipher"))
        assertTrue(setting.contains("fun upsert(key: String, value: String)"))
    }
}

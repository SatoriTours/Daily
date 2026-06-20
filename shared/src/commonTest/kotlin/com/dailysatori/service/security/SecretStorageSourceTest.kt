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
        val externalFavorites =
            File("src/commonMain/kotlin/com/dailysatori/data/repository/ExternalFavoriteSourceRepository.kt").readText()

        assertTrue(ai.contains("secretCipher.encrypt(apiToken)"))
        assertTrue(mcp.contains("secretCipher.encrypt(apiKey)"))
        assertTrue(remote.contains("secretCipher.encrypt(apiToken.trim())"))
        assertTrue(externalFavorites.contains("secretCipher.encrypt(value)"))
    }

    @Test
    fun sensitiveRepositoriesEncryptExistingPlaintextSecrets() {
        val processor = File("src/commonMain/kotlin/com/dailysatori/service/security/SecretFieldProcessor.kt").readText()
        val app = File("../app/src/main/kotlin/com/dailysatori/DailySatoriApplication.kt").readText()

        assertTrue(processor.contains("fun encryptPlaintextSecrets()"))
        assertTrue(processor.contains("SecretFieldRegistry.fields"))
        assertTrue(processor.contains("SecretFieldSpec(table = \"ai_config\", column = \"api_token\")"))
        assertTrue(processor.contains("SecretFieldSpec(table = \"mcp_server\", column = \"api_key\")"))
        assertTrue(processor.contains("SecretFieldSpec(table = \"remote_news_source\", column = \"api_token\")"))
        assertTrue(processor.contains("SecretFieldSpec(table = \"external_favorite_source\", column = \"auth_json\")"))
        assertTrue(processor.contains("SecretFieldSpec(table = \"skill_config\", column = \"api_token\")"))
        assertTrue(processor.contains("whereClause = \"key = '\${SettingKeys.weReadApiKey}'\""))
        assertTrue(app.contains("encryptStoredSecrets()"))
        assertTrue(app.contains("SecretFieldProcessor"))
        assertTrue(app.contains("encryptPlaintextSecrets()"))
    }

    @Test
    fun sensitiveRepositoriesDecryptSecretsAfterReading() {
        val ai = File("src/commonMain/kotlin/com/dailysatori/data/repository/AIConfigRepository.kt").readText()
        val mcp = File("src/commonMain/kotlin/com/dailysatori/data/repository/McpServerRepository.kt").readText()
        val remote = File("src/commonMain/kotlin/com/dailysatori/data/repository/RemoteNewsSourceRepository.kt").readText()
        val externalFavorites =
            File("src/commonMain/kotlin/com/dailysatori/data/repository/ExternalFavoriteSourceRepository.kt").readText()

        assertTrue(ai.contains("decryptConfig"))
        assertTrue(ai.contains("secretCipher.decrypt(config.api_token)"))
        assertTrue(mcp.contains("decryptServer"))
        assertTrue(mcp.contains("secretCipher.decrypt(server.api_key)"))
        assertTrue(remote.contains("decryptSource"))
        assertTrue(remote.contains("secretCipher.decrypt(source.api_token)"))
        assertTrue(externalFavorites.contains("decryptSource"))
        assertTrue(externalFavorites.contains("secretCipher.decrypt(value)"))
    }

    @Test
    fun webServerTokenRemainsInSettingRepository() {
        val setting = File("src/commonMain/kotlin/com/dailysatori/data/repository/SettingRepository.kt").readText()

        assertFalse(setting.contains("SecretCipher"))
        assertTrue(setting.contains("fun upsert(key: String, value: String)"))
    }
}

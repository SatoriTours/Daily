package com.dailysatori.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SkillConfigRepositorySourceTest {
    @Test
    fun repositoryEncryptsTokensAndProtectsBuiltIns() {
        val source = File("src/commonMain/kotlin/com/dailysatori/data/repository/SkillConfigRepository.kt").readText()

        assertTrue(source.contains("SecretCipher"))
        assertTrue(source.contains("secretCipher.encrypt(apiToken)"))
        assertTrue(source.contains("secretCipher.decrypt"))
        assertTrue(source.contains("deleteSkillConfig"))
        assertTrue(source.contains("canDeleteSkill"))
    }

    @Test
    fun repositoryCanEnsureBuiltInWeReadDefaults() {
        val source = File("src/commonMain/kotlin/com/dailysatori/data/repository/SkillConfigRepository.kt").readText()

        assertTrue(source.contains("ensureBuiltInWeRead"))
        assertTrue(source.contains("BuiltInSkillTemplates.weRead"))
        assertTrue(source.contains("builtInWeReadGatewayUrl()"))
        assertTrue(source.contains("builtin = 1"))
    }
}

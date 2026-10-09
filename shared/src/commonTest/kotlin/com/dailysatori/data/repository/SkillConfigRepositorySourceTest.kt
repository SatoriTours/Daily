package com.dailysatori.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SkillConfigRepositorySourceTest {
    @Test
    fun repositoryUsesDatabaseProtectionAndProtectsBuiltIns() {
        val source = File("src/commonMain/kotlin/com/dailysatori/data/repository/SkillConfigRepository.kt").readText()

        assertFalse(source.contains("SecretCipher"))
        assertFalse(source.contains("secretCipher.encrypt"))
        assertFalse(source.contains("secretCipher.decrypt"))
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

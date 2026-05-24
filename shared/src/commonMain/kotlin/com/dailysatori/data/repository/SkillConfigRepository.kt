package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.service.security.SecretCipher
import com.dailysatori.service.skill.BuiltInSkillTemplates
import com.dailysatori.service.skill.builtInWeReadDescription
import com.dailysatori.service.skill.builtInWeReadGatewayUrl
import com.dailysatori.service.skill.builtInWeReadSkillName
import com.dailysatori.service.skill.builtInWeReadSkillVersion
import com.dailysatori.service.skill.canDeleteSkill
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Skill_config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SkillConfigRepository internal constructor(
    private val db: DailySatoriDatabase,
    private val encryptSecret: (String) -> String,
    private val decryptSecret: (String) -> String,
) {
    constructor(db: DailySatoriDatabase, secretCipher: SecretCipher) : this(
        db = db,
        encryptSecret = { apiToken -> secretCipher.encrypt(apiToken) },
        decryptSecret = { encryptedToken -> secretCipher.decrypt(encryptedToken) },
    )

    private val q get() = db.dailySatoriQueries

    fun getAll(): Flow<List<Skill_config>> =
        q.selectAllSkillConfigs().asFlow().mapToList(Dispatchers.IO).map { skills ->
            skills.map(::decryptSkill)
        }

    fun getById(id: Long): Skill_config? = q.selectSkillConfigById(id).executeAsOneOrNull()?.let(::decryptSkill)

    fun getByTemplateId(templateId: String): Skill_config? =
        q.selectSkillConfigByTemplateId(templateId).executeAsOneOrNull()?.let(::decryptSkill)

    fun getBuiltInByTemplateId(templateId: String): Skill_config? =
        q.selectBuiltInSkillConfigByTemplateId(templateId).executeAsOneOrNull()?.let(::decryptSkill)

    fun getEnabled(): List<Skill_config> = q.selectEnabledSkillConfigs().executeAsList().map(::decryptSkill)

    fun insert(
        name: String,
        description: String,
        gatewayUrl: String,
        apiToken: String,
        skillVersion: String,
        enabled: Long,
        builtin: Long = 0,
        provider: String = "",
        templateId: String = "",
        toolSchemaJson: String = "",
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertSkillConfig(
            name, description, gatewayUrl, encryptSecret(apiToken), skillVersion, enabled,
            builtin, provider, templateId, toolSchemaJson, now, now,
        )
    }

    fun update(
        id: Long,
        name: String,
        description: String,
        gatewayUrl: String,
        apiToken: String,
        skillVersion: String,
        enabled: Long,
        provider: String,
        templateId: String,
        toolSchemaJson: String,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateSkillConfig(
            name, description, gatewayUrl, encryptSecret(apiToken), skillVersion,
            enabled, provider, templateId, toolSchemaJson, now, id,
        )
    }

    fun delete(id: Long) {
        val skill = getById(id) ?: return
        if (canDeleteSkill(skill.builtin)) q.deleteSkillConfig(id)
    }

    fun ensureBuiltInWeRead() {
        q.transaction {
            val existing = getBuiltInByTemplateId(BuiltInSkillTemplates.weRead)
            if (existing != null) return@transaction
            insert(
                name = builtInWeReadSkillName(),
                description = builtInWeReadDescription(),
                gatewayUrl = builtInWeReadGatewayUrl(),
                apiToken = "",
                skillVersion = builtInWeReadSkillVersion(),
                enabled = 0,
                builtin = 1,
                provider = BuiltInSkillTemplates.weRead,
                templateId = BuiltInSkillTemplates.weRead,
            )
        }
    }

    private fun decryptSkill(skill: Skill_config): Skill_config =
        skill.copy(api_token = decryptSecret(skill.api_token))
}

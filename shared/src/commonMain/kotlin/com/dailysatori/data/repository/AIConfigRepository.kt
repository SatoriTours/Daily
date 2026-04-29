package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.shared.db.Ai_config
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class AIConfigRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): Flow<List<Ai_config>> =
        q.selectAllAiConfigs().asFlow().mapToList(Dispatchers.IO)

    fun getById(id: Long) = q.selectAiConfigById(id).executeAsOneOrNull()

    fun getDefaultByType(functionType: Long) =
        q.selectDefaultAiConfig(functionType).executeAsOneOrNull()

    fun getGeneralConfig() =
        q.selectGeneralAiConfig().executeAsOneOrNull()

    fun insert(
        name: String,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        functionType: Long = 0,
        inheritFromGeneral: Long = 0,
        isDefault: Long = 0,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertAiConfig(
            name, apiAddress, apiToken, modelName,
            functionType, inheritFromGeneral, isDefault, now, now,
        )
    }

    fun update(
        id: Long,
        name: String,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        functionType: Long,
        inheritFromGeneral: Long,
        isDefault: Long,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateAiConfig(
            name, apiAddress, apiToken, modelName,
            functionType, inheritFromGeneral, isDefault, now, id,
        )
    }

    fun delete(id: Long) = q.deleteAiConfig(id)

    fun initDefaultConfigs() {
        val types = listOf(0L, 1L, 2L, 3L)
        val names = listOf("通用配置", "文章分析", "书籍解读", "日记总结")
        types.forEachIndexed { index, type ->
            if (getDefaultByType(type) == null) {
                insert(names[index], "", "", "", type, if (type == 0L) 0L else 1L, 1L)
            }
        }
    }
}

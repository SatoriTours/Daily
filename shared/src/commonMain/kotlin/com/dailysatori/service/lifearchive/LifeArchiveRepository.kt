package com.dailysatori.service.lifearchive

interface LifeArchiveRepository {
    suspend fun records(): List<LifeArchiveRecord>
    suspend fun categories(): List<LifeArchiveCategory>
    suspend fun save(record: LifeArchiveRecord, expectedUpdatedAt: Long?): LifeArchiveRecord
    suspend fun delete(id: String)
    suspend fun addCategory(name: String): LifeArchiveCategory
    suspend fun renameCategory(id: String, name: String)
    suspend fun deleteCategory(id: String, replacementId: String)
}

fun validateLifeArchiveRecord(record: LifeArchiveRecord, categories: List<LifeArchiveCategory>) {
    require(record.id.isNotBlank() && record.id.length <= 128)
    require(record.title.isNotBlank() && record.title.length <= 120)
    require(categories.any { it.id == record.categoryId })
    require(record.body.length <= 20_000 && record.fields.size <= 50)
    require(record.fields.all { it.name.isNotBlank() && it.name.length <= 80 && it.value.length <= 20_000 })
    require(record.fields.map { it.name.trim().lowercase() }.distinct().size == record.fields.size)
    require((record.sourceReminderId == null) == (record.sourceReminderVersion == null))
    require(record.sourceReminderId == null || record.sourceReminderId.isNotBlank())
    require(record.sourceReminderVersion == null || record.sourceReminderVersion >= 0)
}

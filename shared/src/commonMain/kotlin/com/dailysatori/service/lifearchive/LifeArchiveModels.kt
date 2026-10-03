package com.dailysatori.service.lifearchive

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class LifeArchiveField(val name: String, val value: String)

@Serializable
data class LifeArchiveCategory(val id: String, val name: String)

@Serializable
data class LifeArchiveRecord(
    val id: String,
    val title: String,
    val categoryId: String,
    val body: String = "",
    val fields: List<LifeArchiveField> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val sourceReminderId: String? = null,
    val sourceReminderVersion: Long? = null,
)

fun defaultLifeArchiveCategories(): List<LifeArchiveCategory> = listOf(
    LifeArchiveCategory("domain", "域名"), LifeArchiveCategory("subscription", "订阅"),
    LifeArchiveCategory("payment", "支付"), LifeArchiveCategory("other", "其他"),
)

fun newLifeArchiveId(): String = "${Clock.System.now().toEpochMilliseconds()}_${Random.nextLong().toULong().toString(16)}"

class LifeArchiveInvalidResponse : IllegalArgumentException("AI 返回的资料格式不正确，请重新整理")
class LifeArchiveConflict : IllegalStateException("资料或来源已变化，请重新加载")
class LifeArchiveStorageFailure : IllegalStateException("无法读取或保存生活档案，请保留原文件")

enum class LifeArchiveError { AI_CONFIG, AI_RESPONSE, AI_REQUEST, STORAGE, CONFLICT, INPUT, IMPORT_EMPTY }

fun LifeArchiveRecord.sameContent(other: LifeArchiveRecord): Boolean =
    title == other.title && categoryId == other.categoryId && body == other.body && fields == other.fields

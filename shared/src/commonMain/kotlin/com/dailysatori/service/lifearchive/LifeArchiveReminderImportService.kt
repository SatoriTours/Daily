package com.dailysatori.service.lifearchive

import com.dailysatori.data.repository.ReminderRepository
import com.dailysatori.service.reminder.Reminder
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*

interface LifeArchiveReminderSource {
    suspend fun all(): List<Reminder>
    suspend fun get(id: String): Reminder?
}

class RepositoryLifeArchiveReminderSource(private val repository: ReminderRepository) : LifeArchiveReminderSource {
    override suspend fun all(): List<Reminder> = repository.observeAll().first()
    override suspend fun get(id: String): Reminder? = repository.get(id)
}

fun interface LifeArchiveReminderRemote {
    suspend fun analyze(input: JsonObject): String
}

interface LifeArchiveImporter {
    suspend fun analyze(
        reminders: List<Reminder>, existing: List<LifeArchiveRecord>, categories: List<LifeArchiveCategory>,
        onBatch: (List<LifeArchiveRecord>, Int, Int) -> Unit = { _, _, _ -> },
    ): List<LifeArchiveRecord>
}

class LifeArchiveReminderImportService(private val remote: LifeArchiveReminderRemote) : LifeArchiveImporter {
    constructor(ai: LifeArchiveAiService) : this(LifeArchiveReminderRemote { ai.request(it, IMPORT_RULES) })
    private val codec = LifeArchiveAiCodec()

    override suspend fun analyze(
        reminders: List<Reminder>, existing: List<LifeArchiveRecord>, categories: List<LifeArchiveCategory>,
        onBatch: (List<LifeArchiveRecord>, Int, Int) -> Unit,
    ): List<LifeArchiveRecord> {
        val previous = existing.filter { it.sourceReminderId != null }.associateBy { it.sourceReminderId!! }
        val changed = reminders.filter { previous[it.id]?.sourceReminderVersion != it.version }
        val batches = partition(changed, previous, categories)
        val results = mutableListOf<LifeArchiveRecord>()
        batches.forEachIndexed { index, batch ->
            val response = remote.analyze(input(batch, previous, categories))
            val drafts = codec.decode(response, categories, editing = false, importing = true)
            val resolved = resolve(drafts, batch.associateBy { it.id }, previous)
            results += resolved
            onBatch(resolved, index + 1, batches.size)
        }
        return results
    }

    private fun resolve(
        drafts: List<LifeArchiveRecord>, sources: Map<String, Reminder>, previous: Map<String, LifeArchiveRecord>,
    ): List<LifeArchiveRecord> {
        if (drafts.map { it.sourceReminderId }.distinct().size != drafts.size) throw LifeArchiveInvalidResponse()
        return drafts.mapNotNull { draft ->
            val id = requireNotNull(draft.sourceReminderId)
            val source = sources[id] ?: throw LifeArchiveInvalidResponse()
            val old = previous[id]
            val merged = if (old != null) preserveUnmentioned(old, draft, "同步待办信息，保留现有资料") else draft
            if (old != null && merged.sameContent(old)) return@mapNotNull null
            merged.copy(id = old?.id ?: draft.id, createdAt = old?.createdAt ?: 0, updatedAt = old?.updatedAt ?: 0,
                sourceReminderId = id, sourceReminderVersion = source.version)
        }
    }

    private fun partition(
        reminders: List<Reminder>, previous: Map<String, LifeArchiveRecord>, categories: List<LifeArchiveCategory>,
    ): List<List<Reminder>> {
        val batches = mutableListOf<List<Reminder>>()
        var current = emptyList<Reminder>()
        reminders.forEach { reminder ->
            val candidate = current + reminder
            if (candidate.size > 20 || input(candidate, previous, categories).toString().length > 20_000) {
                require(current.isNotEmpty())
                batches += current
                current = emptyList()
            }
            current += reminder
            require(input(current, previous, categories).toString().length <= 20_000)
        }
        if (current.isNotEmpty()) batches += current
        return batches
    }

    private fun input(
        reminders: List<Reminder>, previous: Map<String, LifeArchiveRecord>, categories: List<LifeArchiveCategory>,
    ): JsonObject = buildJsonObject {
        put("mode", "importReminders")
        put("categories", categoryJson(categories))
        put("reminders", JsonArray(reminders.map { reminder -> buildJsonObject {
            put("id", reminder.id); put("content", reminder.content); put("status", reminder.status.name)
            put("reminderSchedule", buildJsonObject {
                put("startDate", reminder.startDate.toString()); put("endDate", reminder.endDate.toString())
                put("time", reminder.firstReminderTime.toString()); put("recurrence", reminder.recurrence.toString())
            })
            previous[reminder.id]?.let { put("existingRecord", it.toAiJson()) }
        } }))
    }
}

private const val IMPORT_RULES = """
当前任务是从待办中识别域名续费、订阅、服务扣款等长期资料，普通购物/临时任务跳过，不靠单一关键词判断。
返回的每条资料增加 sourceReminderId，必须是本次输入的id，同一id最多一条；无匹配项返回 {"records":[]}。
reminderSchedule是提醒计划，不等于实际到期或扣款时间；除非待办正文明确说明，否则只能标为提醒日期。
待办完成/取消不等于订阅已停用。已有资料保留手动备注及未涉及字段，来源明确更新的信息才替换。
"""

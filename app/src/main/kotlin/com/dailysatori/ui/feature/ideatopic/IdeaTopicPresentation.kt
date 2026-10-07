package com.dailysatori.ui.feature.ideatopic

import com.dailysatori.service.ideatopic.IdeaAiRequestFailedEventPayload
import com.dailysatori.service.ideatopic.IdeaCapturedEventPayload
import com.dailysatori.service.ideatopic.IdeaContentUpdatedEventPayload
import com.dailysatori.service.ideatopic.IdeaDraftEventPayload
import com.dailysatori.service.ideatopic.IdeaDraftResolvedEventPayload
import com.dailysatori.service.ideatopic.IdeaEventKinds
import com.dailysatori.service.ideatopic.IdeaMergedEventPayload
import com.dailysatori.service.ideatopic.IdeaProgressEventPayload
import com.dailysatori.service.ideatopic.IdeaSourceTypes
import com.dailysatori.service.ideatopic.IdeaStatusChangedEventPayload
import com.dailysatori.service.ideatopic.IdeaTopicEvent
import com.dailysatori.service.ideatopic.IdeaTopicStatus
import kotlinx.serialization.json.Json

data class IdeaEventPresentation(
    val title: String,
    val description: String,
    val originalAttribution: String? = null,
    val timeMillis: Long,
)

fun ideaTopicStatusLabelKey(status: IdeaTopicStatus): String = when (status) {
    IdeaTopicStatus.PendingResearch -> "idea_topic.status.to_research"
    IdeaTopicStatus.Researching -> "idea_topic.status.researching"
    IdeaTopicStatus.Advancing -> "idea_topic.status.in_progress"
    IdeaTopicStatus.Completed -> "idea_topic.status.completed"
}

private val presentationJson = Json { ignoreUnknownKeys = true }

fun formatIdeaTopicEvent(event: IdeaTopicEvent, currentTopicId: String? = null): IdeaEventPresentation {
    val attribution = if (currentTopicId != null && event.originalTopicId != currentTopicId) {
        "来自已合并主题 (${event.originalTopicId})"
    } else null

    return when (event.kind) {
        IdeaEventKinds.Captured -> {
            val payload = runCatching { presentationJson.decodeFromString<IdeaCapturedEventPayload>(event.payload) }.getOrNull()
            val sourceType = if (payload?.snapshot?.key?.type == IdeaSourceTypes.Diary) "日记" else "产品机会"
            val title = "从 $sourceType 收录"
            val desc = payload?.snapshot?.originalTitle?.takeIf { it.isNotBlank() }
                ?: payload?.initialContent?.title
                ?: "收录新点子"
            IdeaEventPresentation(
                title = title,
                description = desc,
                originalAttribution = attribution,
                timeMillis = event.createdAt,
            )
        }
        IdeaEventKinds.Progress -> {
            val payload = runCatching { presentationJson.decodeFromString<IdeaProgressEventPayload>(event.payload) }.getOrNull()
            IdeaEventPresentation(
                title = "追加进展",
                description = payload?.text.orEmpty(),
                originalAttribution = attribution,
                timeMillis = event.createdAt,
            )
        }
        IdeaEventKinds.Merged -> {
            val payload = runCatching { presentationJson.decodeFromString<IdeaMergedEventPayload>(event.payload) }.getOrNull()
            val fromTitle = payload?.fromTitle?.ifBlank { payload.fromTopicId } ?: "其他主题"
            val intoTitle = payload?.intoTitle?.ifBlank { payload.intoTopicId } ?: "主主题"
            IdeaEventPresentation(
                title = "主题合并",
                description = "从「$fromTitle」合入「$intoTitle」",
                originalAttribution = attribution,
                timeMillis = event.createdAt,
            )
        }
        IdeaEventKinds.ContentUpdated -> {
            val payload = runCatching { presentationJson.decodeFromString<IdeaContentUpdatedEventPayload>(event.payload) }.getOrNull()
            val changes = buildList {
                if (payload?.before?.title != payload?.after?.title) add("标题")
                if (payload?.before?.description != payload?.after?.description) add("描述")
                if (payload?.before?.provenanceSummary != payload?.after?.provenanceSummary) add("来龙去脉")
                if (payload?.before?.conclusions != payload?.after?.conclusions) add("研究结论")
                if (payload?.before?.nextAction != payload?.after?.nextAction) add("下一步")
            }
            val desc = if (changes.isNotEmpty()) "更新了 " + changes.joinToString("、") else "更新正式内容"
            IdeaEventPresentation(
                title = "修订正式内容",
                description = desc,
                originalAttribution = attribution,
                timeMillis = event.createdAt,
            )
        }
        IdeaEventKinds.StatusChanged -> {
            val payload = runCatching { presentationJson.decodeFromString<IdeaStatusChangedEventPayload>(event.payload) }.getOrNull()
            val fromStatus = payload?.from?.let { IdeaTopicStatus.fromStorage(it) } ?: IdeaTopicStatus.PendingResearch
            val toStatus = payload?.to?.let { IdeaTopicStatus.fromStorage(it) } ?: IdeaTopicStatus.PendingResearch
            IdeaEventPresentation(
                title = "状态变更",
                description = "从 ${statusDisplay(fromStatus)} 变更为 ${statusDisplay(toStatus)}",
                originalAttribution = attribution,
                timeMillis = event.createdAt,
            )
        }
        IdeaEventKinds.DraftProposed -> {
            val payload = runCatching { presentationJson.decodeFromString<IdeaDraftEventPayload>(event.payload) }.getOrNull()
            IdeaEventPresentation(
                title = "AI 提出完善草稿",
                description = payload?.content?.title?.let { "建议标题: $it" } ?: "草稿已生成",
                originalAttribution = attribution,
                timeMillis = event.createdAt,
            )
        }
        IdeaEventKinds.DraftApplied -> {
            val payload = runCatching { presentationJson.decodeFromString<IdeaDraftResolvedEventPayload>(event.payload) }.getOrNull()
            IdeaEventPresentation(
                title = "确认应用草稿",
                description = payload?.after?.title?.let { "新标题: $it" } ?: "草稿已确认生效",
                originalAttribution = attribution,
                timeMillis = event.createdAt,
            )
        }
        IdeaEventKinds.DraftDiscarded -> {
            IdeaEventPresentation(
                title = "放弃草稿",
                description = "AI 完善草稿已放弃",
                originalAttribution = attribution,
                timeMillis = event.createdAt,
            )
        }
        IdeaEventKinds.AiRequestFailed -> {
            val payload = runCatching { presentationJson.decodeFromString<IdeaAiRequestFailedEventPayload>(event.payload) }.getOrNull()
            IdeaEventPresentation(
                title = "AI 请求失败",
                description = payload?.reason?.let { "原因: $it" } ?: "请求异常",
                originalAttribution = attribution,
                timeMillis = event.createdAt,
            )
        }
        else -> {
            IdeaEventPresentation(
                title = "主题动态",
                description = event.kind,
                originalAttribution = attribution,
                timeMillis = event.createdAt,
            )
        }
    }
}

private fun statusDisplay(status: IdeaTopicStatus): String = when (status) {
    IdeaTopicStatus.PendingResearch -> "待研究"
    IdeaTopicStatus.Researching -> "研究中"
    IdeaTopicStatus.Advancing -> "推进中"
    IdeaTopicStatus.Completed -> "已完成"
}

package com.dailysatori.service.diary

import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.DiaryTagRepository
import com.dailysatori.data.repository.DiaryThreadRepository
import com.dailysatori.service.asynctask.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DiaryTagCoordinator(
    private val tags: DiaryTagRepository,
    private val tasks: AsyncTaskRepository,
    private val generator: DiaryTagGenerator,
    private val threads: DiaryThreadRepository,
    private val configured: () -> Boolean,
) : AsyncTaskHandler {
    override val type = AsyncTaskType.diary_tag_generate.name
    private var observer: Job? = null
    private val execution = Mutex()

    fun enqueue(id: Long, force: Boolean = false, missingOnly: Boolean = false): Long? {
        if (!configured()) return null
        val snapshot = tags.prepare(id, force) ?: return null
        if (missingOnly && parseDiaryTags(snapshot.tags).isNotEmpty()) return null
        val payload = TagPayload(id, diaryTagFingerprint(snapshot.content), snapshot.state.revision, snapshot.policy, force, missingOnly)
        return tasks.enqueue(type, Json.encodeToString(payload),
            uniqueKey = "diary-tags:$id:${payload.fingerprint}:${payload.revision}:${payload.policy}:$force:$missingOnly", maxAttempts = 3)
    }

    fun start(scope: CoroutineScope, schedule: (Long) -> Unit) {
        if (observer?.isActive == true) return
        observer = scope.launch(Dispatchers.IO) {
            threads.observeSources().map { sources -> sources.map { it.rootId to it.revision } }
                .distinctUntilChanged().collect {
                    val pending = mutableListOf<Long>()
                    tags.changedDiaryIds { ids -> ids.mapNotNullTo(pending) { id -> enqueue(id) } }
                    pending.forEach(schedule)
                }
        }
    }

    override suspend fun execute(taskId: Long, payloadJson: String, checkpointJson: String,
        reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult = execution.withLock {
        executePending(payloadJson, reporter)
    }

    private suspend fun executePending(payloadJson: String, reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult {
        val payload = runCatching { Json.decodeFromString<TagPayload>(payloadJson) }.getOrNull()
            ?: return AsyncTaskExecutionResult.PermanentFailure("invalid_payload", "标签任务无效")
        val snapshot = tags.prepare(payload.diaryId, payload.force) ?: return AsyncTaskExecutionResult.Success()
        if (diaryTagFingerprint(snapshot.content) != payload.fingerprint || snapshot.state.revision != payload.revision ||
            snapshot.policy != payload.policy || (payload.missingOnly && parseDiaryTags(snapshot.tags).isNotEmpty())) {
            return AsyncTaskExecutionResult.Success()
        }
        return try {
            reporter.report(0, 1, "正在生成日记标签")
            val vocabulary = tags.vocabulary()
            val result = generator.generate(snapshot.content, vocabulary.names, vocabulary.aliases)
            if (tags.apply(snapshot, result.tags)) {
                tags.suggest(result.merges)
                tags.suggestPending(result.pendingTags)
            }
            reporter.report(1, 1, "日记标签已整理")
            AsyncTaskExecutionResult.Success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IllegalArgumentException) {
            AsyncTaskExecutionResult.PermanentFailure("tag_format", "标签结果无效，请手动重试")
        } catch (_: Exception) {
            AsyncTaskExecutionResult.RetryableFailure("tag_request", "标签生成失败，请检查 AI 配置或稍后重试")
        }
    }
}

fun diaryTagTaskDiaryId(payload: String): Long? = runCatching { Json.decodeFromString<TagPayload>(payload).diaryId }.getOrNull()

@Serializable
private data class TagPayload(val diaryId: Long, val fingerprint: String, val revision: Long,
    val policy: String?, val force: Boolean, val missingOnly: Boolean)

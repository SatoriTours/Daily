package com.dailysatori.service.diary

import co.touchlab.kermit.Logger
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.DiaryThreadRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.asynctask.AsyncTaskType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 日记续写汇总的持久任务处理者：版本绑定写入、过期结果丢弃和启动恢复都集中在这里。
 * 原文失效状态由数据层触发器维护，是补建任务的事实来源。
 */
class DiaryThreadSummaryCoordinator(
    private val threads: DiaryThreadRepository,
    private val tasks: AsyncTaskRepository,
    private val generator: DiaryThreadSummaryGenerator,
    private val configured: () -> Boolean,
) : AsyncTaskHandler {
    override val type = AsyncTaskType.diary_thread_summarize.name

    private val log = Logger.withTag("DiaryThreadSummary")
    private var observer: Job? = null

    /** 单篇无续写的日记不排队；相同日记相同版本复用同一任务。 */
    fun enqueue(rootId: Long, force: Boolean = false): Long? {
        val snapshot = threads.getSnapshot(rootId) ?: return null
        if (snapshot.entries.size <= 1 && !force) return null
        if (!configured()) {
            // 配置缺失也必须留下同版本可解释状态，否则界面只能无限显示生成中。
            threads.markSummaryState(rootId, snapshot.revision, DiaryThreadSummaryStatus.failed, MISSING_AI_MESSAGE)
            return null
        }
        return tasks.enqueue(
            type = type,
            payloadJson = Json.encodeToString(SummaryPayload(rootId, snapshot.revision)),
            uniqueKey = uniqueKey(rootId, snapshot.revision),
            maxAttempts = 3,
        )
    }

    /** 启动恢复入口：只补有续写且待汇总的主日记；配置缺失时写入可重试的失败状态。 */
    fun recoverPending(): List<Long> {
        val pending = threads.pendingSummaryRootIds()
        if (configured()) return pending
        pending.forEach { rootId ->
            threads.getSnapshot(rootId)?.let { snapshot ->
                threads.markSummaryState(rootId, snapshot.revision, DiaryThreadSummaryStatus.failed, MISSING_AI_MESSAGE)
            }
        }
        return emptyList()
    }

    fun start(scope: CoroutineScope, schedule: (Long) -> Unit) {
        if (observer?.isActive == true) return
        observer = scope.launch(Dispatchers.IO) {
            try {
                threads.observeSources().map { sources -> sources.map { it.rootId to it.revision } }
                    .distinctUntilChanged().collect {
                        recoverPending().forEach { rootId ->
                            try {
                                enqueue(rootId)?.let { taskId -> schedule(taskId) }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                // 排队或唤醒失败保留待生成事实，后续原文变化或下次启动再补。
                                log.w { "Diary thread summary enqueue failed for $rootId (${error::class.simpleName})" }
                            }
                        }
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                log.w { "Diary thread summary observation failed (${error::class.simpleName})" }
            }
        }
    }

    override suspend fun execute(
        taskId: Long,
        payloadJson: String,
        checkpointJson: String,
        reporter: AsyncTaskProgressReporter,
    ): AsyncTaskExecutionResult {
        val payload = runCatching { Json.decodeFromString<SummaryPayload>(payloadJson) }.getOrNull()
            ?: return AsyncTaskExecutionResult.PermanentFailure("invalid_payload", "汇总任务无效")
        // 已删除的日记不得复活：结果与任务都直接结束。
        val snapshot = threads.getSnapshot(payload.rootId) ?: return AsyncTaskExecutionResult.Success()
        if (snapshot.entries.size <= 1) return AsyncTaskExecutionResult.Success()
        // 只有录音占位正文时不用占位文本生成汇总；转写完成后原文版本变化会再次触发。
        if (chunkThreadEntries(snapshot.entries).isEmpty()) return AsyncTaskExecutionResult.Success()
        if (snapshot.revision != payload.revision) {
            // 过期任务快速退出，并确保最新版本有待执行任务。
            enqueue(payload.rootId)
            return AsyncTaskExecutionResult.Success()
        }
        threads.markSummaryState(payload.rootId, payload.revision, DiaryThreadSummaryStatus.running)
        return try {
            reporter.report(0, 1, "正在整理日记续写")
            val summary = generator.generate(snapshot, checkpointJson) { checkpoint ->
                reporter.report(0, 1, "正在整理日记续写", checkpoint)
            }
            threads.commitSummary(payload.rootId, payload.revision, summary)
            reporter.report(1, 1, "日记续写汇总已完成")
            AsyncTaskExecutionResult.Success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (invalid: IllegalArgumentException) {
            threads.markSummaryState(
                payload.rootId,
                payload.revision,
                DiaryThreadSummaryStatus.failed,
                invalid.message ?: "汇总结果无效",
            )
            AsyncTaskExecutionResult.PermanentFailure("summary_format", invalid.message ?: "汇总结果无效")
        } catch (error: Exception) {
            log.w { "Diary thread summary failed for ${payload.rootId} (${error::class.simpleName})" }
            threads.markSummaryState(
                payload.rootId,
                payload.revision,
                DiaryThreadSummaryStatus.failed,
                error.message.orEmpty(),
            )
            AsyncTaskExecutionResult.RetryableFailure("summary_request", error.message.orEmpty())
        }
    }

    private fun uniqueKey(rootId: Long, revision: Long): String = "diary-thread-summary:$rootId:$revision"
}

@Serializable
private data class SummaryPayload(val rootId: Long, val revision: Long)

internal const val MISSING_AI_MESSAGE = "未配置默认 AI 模型，配置后可重试"

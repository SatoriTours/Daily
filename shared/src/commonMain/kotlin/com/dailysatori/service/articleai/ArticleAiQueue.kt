package com.dailysatori.service.articleai

import com.dailysatori.data.repository.ArticleAiRepository
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.ai.AiHttpFailure
import com.dailysatori.service.asynctask.*
import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.Article_ai_job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

class ArticleAiQueue(
    private val repo: ArticleAiRepository,
    private val articles: ArticleRepository,
    private val tasks: AsyncTaskRepository,
    private val remote: ArticleAiRemote,
    private val schedule: (Long) -> Unit,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : AsyncTaskHandler {
    override val type = AsyncTaskType.article_ai.name
    private val owner = Mutex()
    private var recovered = false

    fun onNewBody(article: Article) = enqueue(article.id, null, ArticleAiRepository.NEW)

    fun manual(id: Long, mode: ArticleAiMode? = null, redo: Boolean = false) =
        enqueue(id, mode, ArticleAiRepository.MANUAL, redo)

    private fun enqueue(id: Long, mode: ArticleAiMode?, priority: Long, redo: Boolean = false) {
        val wake = repo.transaction {
            val current = articles.getById(id)?.takeIf { it.source_type == "remote_news" } ?: return@transaction null
            repo.enqueue(current, mode, priority, redo) ?: return@transaction null
            if (repo.blocked().isNotEmpty()) null else newWake()
        }
        wake?.let(schedule)
    }

    fun historyStatus() = ArticleAiHistoryStatus(repo.historyCandidates().size, repo.historyWaiting(), repo.historyEnabled(), repo.blocked())

    fun startHistory() {
        val approved = repo.historyCandidates()
        val wake = repo.transaction {
            approved.forEach { original ->
                articles.getById(original.id)?.let { repo.enqueue(it, null, ArticleAiRepository.HISTORY_PRIORITY) }
            }
            repo.setHistoryEnabled(true)
            if (repo.blocked().isEmpty()) newWake() else null
        }
        wake?.let(schedule)
    }

    fun pauseHistory() = repo.setHistoryEnabled(false)

    fun resumeHistory() {
        val wake = repo.transaction { repo.setHistoryEnabled(true); newWake() }
        schedule(wake)
    }

    fun resume() {
        val wake = repo.transaction { repo.block(""); newWake() }
        schedule(wake)
    }

    private fun newWake() = tasks.enqueue(type, "{}")

    override suspend fun execute(
        taskId: Long,
        payloadJson: String,
        checkpointJson: String,
        reporter: AsyncTaskProgressReporter,
    ): AsyncTaskExecutionResult = owner.withLock {
        if (!recovered) {
            repo.recover()
            recovered = true
        }
        if (repo.blocked().isNotEmpty()) return@withLock AsyncTaskExecutionResult.Success()
        val lease = reporter.executionLease
        if (repo.nextTime()?.let { it <= now() } == true) {
            reporter.report(0, 1)
            process(lease)?.let { id ->
                reporter.report(1, 1, checkpointJson = """{"articleAiJobId":$id}""")
            }
        }
        val next = repo.nextTime()?.takeIf { repo.blocked().isEmpty() }
        if (next == null) AsyncTaskExecutionResult.Success() else
            AsyncTaskExecutionResult.Yield((next - now()).coerceAtLeast(500))
    }

    private fun current(job: Article_ai_job): ArticleBody? =
        repo.candidates(job).asSequence().mapNotNull(articles::getById).map(repo::input)
            .firstOrNull { it.kind == ArticleBodyKind.BODY && it.hash == job.body_hash }

    private fun processConfig(job: Article_ai_job) = remote.config().also {
        if (it == null) {
            repo.block("configuration")
            repo.finish(job, "failed", "configuration")
        }
    }

    private suspend fun process(lease: AsyncTaskExecutionLease?): Long? {
        val start = fenced(lease) {
            if (repo.blocked().isNotEmpty()) return@fenced null
            // The reporter may suspend. Select and start in the same transaction so any new
            // manual intent/priority or history pause is respected before the paid boundary.
            val job = repo.pick(now()) ?: return@fenced null
            val body = current(job)
            if (body == null) {
                repo.finish(job, "obsolete")
                return@fenced null
            }
            val config = processConfig(job) ?: return@fenced null
            val input = articleAiInput(body.markdown, ArticleAiMode.valueOf(job.mode), articleModelLimits(config.model_name))
            if (input == null) {
                repo.finish(job, "failed", "budget")
                return@fenced null
            }
            job to Triple(config, input, repo.begin(job, now()))
        } ?: return null
        val (job, prepared) = start
        val (config, input, auditId) = prepared
        try {
            val output = remote.request(config, ArticleAiMode.valueOf(job.mode), input)
            fenced(lease) {
                val valid = current(job) != null
                repo.audit(auditId, if (valid) "succeeded" else "obsolete", output.usage)
                if (valid) repo.save(job, output, input.partial)
                repo.finish(job, if (valid) "succeeded" else "obsolete")
            }
        } catch (cancelled: CancellationException) {
            repo.transaction { repo.audit(auditId, "interrupted"); repo.finish(job, "failed", "interrupted") }
            throw cancelled
        } catch (error: Exception) {
            fenced(lease) { recordFailure(job, auditId, error) }
        } finally {
            if (lease != null && !tasks.hasRunningLease(lease.taskId, lease.owner)) repo.recover()
        }
        return job.id
    }

    private fun recordFailure(job: Article_ai_job, auditId: Long, error: Exception) {
        val status = (error as? AiHttpFailure)?.status
        val invalid = error as? InvalidArticleAiOutput
        val pause = status in setOf(401, 402, 403, 429)
        // A timeout, broken connection or ambiguous gateway response may already have been billed.
        // Only an explicit service-unavailable response is eligible for one automatic retry.
        val retry = status == 503 && job.requests == 0L
        val code = status?.let { "http_$it" } ?: if (invalid != null) "incomplete" else "request_unknown"
        repo.audit(auditId, code, invalid?.usage ?: ArticleAiUsage())
        if (pause) repo.block(code)
        repo.finish(job, if (retry) "queued" else "failed", code, if (retry) now() + 30_000 else 0)
    }

    private fun <T> fenced(lease: AsyncTaskExecutionLease?, block: () -> T): T? =
        repo.transaction { if (lease == null || tasks.hasRunningLease(lease.taskId, lease.owner)) block() else null }
}

data class ArticleAiHistoryStatus(val pending: Int, val waiting: Int, val running: Boolean, val blocked: String)

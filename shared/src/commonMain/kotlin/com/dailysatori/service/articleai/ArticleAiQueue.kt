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
            .firstOrNull {
                it.hash == job.body_hash && it.manualProcessable() &&
                    (it.kind == ArticleBodyKind.BODY || job.priority == ArticleAiRepository.MANUAL)
            }

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
            val inputs = articleReadingInputs(body.markdown, ArticleAiMode.valueOf(job.mode), articleModelLimits(config.model_name))
            if (inputs == null) {
                repo.finish(job, "failed", "budget")
                return@fenced null
            }
            job to Triple(config, inputs, repo.begin(job, now()))
        } ?: return null
        val (job, prepared) = start
        val (config, inputs, auditId) = prepared
        val usages = mutableListOf<ArticleAiUsage>()
        try {
            val output = requestFullArticleReading(articleModelLimits(config.model_name), inputs) { mode, input ->
                check(current(job) != null) { "Article source changed" }
                remote.request(config, mode, input).also { usages += it.usage }
            }
            fenced(lease) {
                val valid = current(job) != null
                repo.audit(auditId, if (valid) "succeeded" else "obsolete", combinedUsage(usages))
                if (valid) repo.save(job, output, partial = false)
                repo.finish(job, if (valid) "succeeded" else "obsolete")
            }
        } catch (cancelled: CancellationException) {
            repo.transaction { repo.audit(auditId, "interrupted"); repo.finish(job, "failed", "interrupted") }
            throw cancelled
        } catch (error: Exception) {
            fenced(lease) {
                if (current(job) == null) {
                    repo.audit(auditId, "obsolete", combinedUsage(usages))
                    repo.finish(job, "obsolete")
                } else recordFailure(job, auditId, error, usages)
            }
        } finally {
            if (lease != null && !tasks.hasRunningLease(lease.taskId, lease.owner)) repo.recover()
        }
        return job.id
    }

    private fun combinedUsage(usages: List<ArticleAiUsage>): ArticleAiUsage {
        fun total(selector: (ArticleAiUsage) -> Long?): Long? =
            usages.mapNotNull(selector).takeIf { it.isNotEmpty() }?.sum()
        return ArticleAiUsage(total { it.input }, total { it.output }, total { it.cached })
    }

    private fun recordFailure(job: Article_ai_job, auditId: Long, error: Exception, usages: List<ArticleAiUsage>) {
        val status = (error as? AiHttpFailure)?.status
        val invalid = error as? InvalidArticleAiOutput
        val pause = status in setOf(401, 402, 403, 429)
        // Do not replay already paid sections; preserve the old complete translation on failure.
        val retry = status == 503 && job.requests == 0L && usages.isEmpty()
        val code = status?.let { "http_$it" } ?: if (invalid != null) "incomplete" else "request_unknown"
        repo.audit(auditId, code, combinedUsage(usages + listOfNotNull(invalid?.usage)))
        if (pause) repo.block(code)
        repo.finish(job, if (retry) "queued" else "failed", code, if (retry) now() + 30_000 else 0)
    }

    private fun <T> fenced(lease: AsyncTaskExecutionLease?, block: () -> T): T? =
        repo.transaction { if (lease == null || tasks.hasRunningLease(lease.taskId, lease.owner)) block() else null }
}

internal suspend fun requestFullArticleReading(
    limits: ArticleModelLimits,
    inputs: List<Pair<ArticleAiMode, ArticleAiInput>>,
    request: suspend (ArticleAiMode, ArticleAiInput) -> ArticleAiOutput,
): ArticleAiOutput {
    val outputs = inputs.map { (mode, input) -> request(mode, input) }
    val summaries = outputs.mapNotNull { it.summary }
    val summary = if (summaries.size <= 1) summaries.singleOrNull()
        else summarizeSections(limits, summaries, request)
    val translation = outputs.mapNotNull { it.translation }.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    return ArticleAiOutput(summary, translation, ArticleAiUsage())
}

private suspend fun summarizeSections(
    limits: ArticleModelLimits,
    summaries: List<String>,
    request: suspend (ArticleAiMode, ArticleAiInput) -> ArticleAiOutput,
): String {
    var sections = summaries
    while (sections.size > 1) {
        val inputs = requireNotNull(articleReadingInputs(sections.joinToString("\n\n"), ArticleAiMode.SUMMARY, limits))
        check(inputs.size < sections.size) { "Article summaries exceed the model budget" }
        sections = inputs.map { (mode, input) -> requireNotNull(request(mode, input).summary) }
    }
    return sections.single()
}

data class ArticleAiHistoryStatus(val pending: Int, val waiting: Int, val running: Boolean, val blocked: String)

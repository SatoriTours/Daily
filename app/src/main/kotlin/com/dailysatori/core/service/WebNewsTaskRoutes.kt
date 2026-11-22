package com.dailysatori.core.service

import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.UnifiedNewsSummaryRepository
import com.dailysatori.service.asynctask.AsyncTaskFilter
import com.dailysatori.service.unifiednews.dailyUnifiedNewsWindowFor
import com.dailysatori.ui.feature.unifiednews.enqueueManualNewsRefresh
import com.dailysatori.ui.feature.unifiednews.hasActiveNewsRefreshTask
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.java.KoinJavaComponent.get

internal fun Route.setupNewsRoutes(enqueue: (Long) -> Unit) {
    route("/news") {
        get("/summary") {
            val repo = get<UnifiedNewsSummaryRepository>(UnifiedNewsSummaryRepository::class.java)
            val summary = repo.getLatestSuccessful()
            if (summary == null) {
                call.respondText(respondOk(buildJsonObject { put("summary", JsonPrimitive("")) }), ContentType.Application.Json)
                return@get
            }
            val sources = repo.getSources(summary.id)
            call.respondText(respondOk(buildJsonObject {
                put("id", JsonPrimitive(summary.id))
                put("date", JsonPrimitive(summary.summary_date))
                put("title", JsonPrimitive(summary.title))
                put("summary", JsonPrimitive(summary.content ?: ""))
                put("generatedAt", JsonPrimitive(summary.generated_at ?: summary.updated_at))
                put("sources", JsonArray(sources.map { source -> buildJsonObject {
                    put("refKey", JsonPrimitive(source.ref_key))
                    put("title", JsonPrimitive(source.title))
                    put("sourceType", JsonPrimitive(source.source_type))
                    put("sourceId", JsonPrimitive(source.source_id ?: 0L))
                    put("url", JsonPrimitive(source.source_url ?: ""))
                } }))
            }), ContentType.Application.Json)
        }

        post("/summary/refresh") {
            // Same manual-refresh identity, dedupe and task lease as the app: no side-channel
            // generation that could overwrite a newer window's summary.
            val tasks = get<AsyncTaskRepository>(AsyncTaskRepository::class.java)
            val submission = enqueueManualNewsRefresh(tasks, isDebugBuild = false) { taskId ->
                enqueue(taskId)
            }
            // Only this request's real task may answer; a historical terminal task is never reused.
            val taskId = submission.taskId
            if (taskId == null) {
                call.respondText(respondFail("新闻汇总刷新任务创建失败"), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            }
            val completed = withTimeoutOrNull(NEWS_REFRESH_WAIT_MS) {
                tasks.observeTaskById(taskId).filterNotNull()
                    .first { !hasActiveNewsRefreshTask(listOf(it.status)) }
            }
            // One mapping for both paths: a task that turned terminal while the wait expired is
            // reported by its real result, never as accepted.
            val task = completed ?: tasks.getById(taskId)
            val window = dailyUnifiedNewsWindowFor()
            val summary = if (completed == null) null else
                get<UnifiedNewsSummaryRepository>(UnifiedNewsSummaryRepository::class.java)
                    .getByWindow(window.summaryDate, window.key.value)
            when (val outcome = newsRefreshOutcome(
                taskId = taskId,
                taskStatus = task?.status.orEmpty(),
                taskError = task?.last_error_message.orEmpty(),
                summaryStatus = summary?.status,
                summaryError = summary?.error_message,
            )) {
                is NewsRefreshOutcome.Completed -> call.respondText(respondOk(buildJsonObject {
                    put("status", JsonPrimitive(outcome.status))
                }), ContentType.Application.Json)
                is NewsRefreshOutcome.Accepted -> call.respondText(respondAccepted(outcome.status, taskId),
                    ContentType.Application.Json, HttpStatusCode.Accepted)
                is NewsRefreshOutcome.Failed -> call.respondText(respondFail(outcome.message),
                    ContentType.Application.Json, HttpStatusCode.BadRequest)
            }
        }
    }
}

internal fun Route.setupTaskRoutes(cancelTask: (Long) -> Unit) {
    route("/tasks") {
        get {
            val showTerminal = call.request.queryParameters["showTerminal"]?.toBooleanStrictOrNull() ?: true
            val page = get<AsyncTaskRepository>(AsyncTaskRepository::class.java)
                .observeTaskCenter(AsyncTaskFilter(showTerminal = showTerminal), limit = 60)
                .first()
            call.respondText(respondOk(buildJsonObject {
                put("items", JsonArray(page.tasks.map { task -> buildJsonObject {
                    put("id", JsonPrimitive(task.id))
                    put("type", JsonPrimitive(task.type))
                    put("status", JsonPrimitive(task.status))
                    put("progressCurrent", JsonPrimitive(task.progressCurrent))
                    put("progressTotal", JsonPrimitive(task.progressTotal))
                    put("progressMessage", JsonPrimitive(task.progressMessage))
                    put("createdAt", JsonPrimitive(task.createdAt))
                    put("updatedAt", JsonPrimitive(task.updatedAt))
                    put("hasError", JsonPrimitive(task.lastErrorMessage.isNotBlank()))
                } }))
                put("hasMore", JsonPrimitive(page.hasMore))
            }), ContentType.Application.Json)
        }

        post("/{id}/cancel") {
            val id = call.parameters["id"]?.toLongOrNull() ?: 0L
            val repo = get<AsyncTaskRepository>(AsyncTaskRepository::class.java)
            if (repo.getById(id) == null) {
                call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
                return@post
            }
            cancelTask(id)
            call.respondText(respondOk(), ContentType.Application.Json)
        }
    }
}

/** Accepted response: the refresh runs on as a durable task; the client does not need to poll. */
private fun respondAccepted(status: String, taskId: Long): String = respondOk(buildJsonObject {
    put("status", JsonPrimitive(status))
    put("taskId", JsonPrimitive(taskId))
})

/** Bounded wait so the LAN client keeps its bounded request and never sees a false success. */
private val NEWS_REFRESH_WAIT_MS = 15_000L

package com.dailysatori.core.task

import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.opportunity.NewsOpportunityService
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class NewsOpportunityTaskHandler(private val service: NewsOpportunityService) : AsyncTaskHandler {
    override val type = TYPE

    override suspend fun execute(taskId: Long, payloadJson: String, checkpointJson: String, reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult {
        return try {
            val automatic = Json.parseToJsonElement(payloadJson).jsonObject["automatic"]?.jsonPrimitive?.booleanOrNull == true
            service.analyze(automatic = automatic) { current, total, message ->
                reporter.report(current.toLong(), total.toLong(), message)
            }
            AsyncTaskExecutionResult.Success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AsyncTaskExecutionResult.PermanentFailure("opportunity_analysis_failed", service.state.value.error ?: "新闻机会点分析未完成，请重试")
        }
    }

    companion object { const val TYPE = "news_opportunity_analysis" }
}

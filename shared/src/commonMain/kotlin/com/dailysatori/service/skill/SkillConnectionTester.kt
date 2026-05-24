package com.dailysatori.service.skill

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SkillConnectionTestRequest(
    val name: String,
    val gatewayUrl: String,
    val apiToken: String,
    val skillVersion: String,
    val provider: String,
    val templateId: String,
    val toolSchemaJson: String,
)

sealed class SkillConnectionTestResult {
    data class Success(val message: String) : SkillConnectionTestResult()
    data class Failure(val message: String) : SkillConnectionTestResult()
}

interface SkillConnectionTester {
    suspend fun test(request: SkillConnectionTestRequest): SkillConnectionTestResult
}

class DefaultSkillConnectionTester(
    private val client: HttpClient,
) : SkillConnectionTester {
    override suspend fun test(request: SkillConnectionTestRequest): SkillConnectionTestResult = try {
        withTimeout(15_000L) {
            val response = client.post(request.gatewayUrl.trim()) {
                contentType(ContentType.Application.Json)
                val token = request.apiToken.trim()
                if (token.isNotBlank()) bearerAuth(token)
                setBody(skillConnectionTestBody(request).toString())
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                SkillConnectionTestResult.Failure("连接失败：HTTP ${response.status.value}")
            } else {
                skillConnectionResultFromBody(body)
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        SkillConnectionTestResult.Failure("连接失败：${error.message ?: error::class.simpleName.orEmpty()}")
    }
}

fun skillConnectionTestBody(request: SkillConnectionTestRequest): JsonObject = buildJsonObject {
    put("api_name", JsonPrimitive(skillConnectionTestApiName(request.templateId)))
    put("skill_version", JsonPrimitive(request.skillVersion.ifBlank { "1.0.0" }))
    if (request.templateId == BuiltInSkillTemplates.weRead) {
        put("keyword", JsonPrimitive("三体"))
        put("scope", JsonPrimitive(10))
        put("count", JsonPrimitive(1))
    } else {
        put("params_json", JsonPrimitive("{}"))
    }
}

fun skillConnectionTestApiName(templateId: String): String =
    if (templateId == BuiltInSkillTemplates.weRead) "/store/search" else "test"

fun skillConnectionResultFromBody(body: String): SkillConnectionTestResult {
    val root = runCatching { skillConnectionJson.parseToJsonElement(body).jsonObject }.getOrNull()
        ?: return SkillConnectionTestResult.Success("连接成功，服务返回非 JSON 内容")
    val errcode = root["errcode"]?.jsonPrimitive?.intOrNull ?: root["code"]?.jsonPrimitive?.intOrNull ?: 0
    if (errcode != 0) {
        val message = root["errmsg"]?.jsonPrimitive?.contentOrNull
            ?: root["message"]?.jsonPrimitive?.contentOrNull
            ?: "服务返回错误：$errcode"
        return SkillConnectionTestResult.Failure("连接失败：$message")
    }
    return SkillConnectionTestResult.Success("连接成功，Skill 服务可用")
}

private val skillConnectionJson = Json { ignoreUnknownKeys = true; isLenient = true }

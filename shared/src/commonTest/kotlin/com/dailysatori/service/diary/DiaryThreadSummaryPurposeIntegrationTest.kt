package com.dailysatori.service.diary

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryThreadRepository
import com.dailysatori.di.sharedModule
import com.dailysatori.service.ai.AiPurpose
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiaryThreadSummaryPurposeIntegrationTest {
    @Test
    fun configuredSummaryUsesAssignedUserExpressionModelAndFastPolicy() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val requests = mutableListOf<JsonObject>()
        val client = HttpClient(MockEngine { request ->
            requests += Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            respond("""{"choices":[{"message":{"content":"{\"summary\":\"整理后的完整经历\"}"}}]}""",
                headers = headersOf("Content-Type", "application/json"))
        })
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val configs = AIConfigRepository(db, TestCipher)
            configs.insert("openai", "https://example.com/v1", "synthetic-token", "fallback-model", 1)
            configs.insert("opencode-go", "https://example.com/v1", "synthetic-token", "deepseek-v4-flash")
            val assigned = configs.getAllSync().single { it.model_name == "deepseek-v4-flash" }
            configs.setPurposeConfig(AiPurpose.INTERACTIVE, assigned.id)
            val rootId = DiaryRepository(db, driver).create("原始经历")
            val threads = DiaryThreadRepository(db, driver)
            threads.createReply(rootId, "补充后续经历")
            val application = koinApplication {
                modules(sharedModule, module {
                    single { db }
                    single<SqlDriver> { driver }
                    single<SecretValueCipher> { TestCipher }
                    single { client }
                })
            }
            try {
                val generator = application.koin.get<DiaryThreadSummaryGenerator>()
                val summary = generator.generate(requireNotNull(threads.getSnapshot(rootId)))
                assertEquals("整理后的完整经历", summary)
                assertTrue(requests.isNotEmpty())
                requests.forEach { body ->
                    assertEquals("deepseek-v4-flash", body["model"]!!.jsonPrimitive.content)
                    assertEquals("disabled", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
                }
            } finally {
                application.close()
            }
        } finally {
            client.close()
            driver.close()
        }
    }

    private object TestCipher : SecretValueCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
        override fun isEncrypted(value: String) = false
    }
}

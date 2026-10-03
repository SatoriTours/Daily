package com.dailysatori.service.diary

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class SpeechTranscriptionApiTest {
    @Test fun openAiUsesSelectedTranscriptionModel() = runBlocking {
        HttpClient(MockEngine { request ->
            assertEquals("/v1/audio/transcriptions", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("gpt-transcribe"))
            assertFalse(body.contains("whisper-1"))
            respond("{\"text\":\"准确转写\"}", HttpStatusCode.OK)
        }).use { client ->
            assertEquals("准确转写", SpeechTranscriptionApi(client).transcribe(
                SpeechConfig("openai", "gpt-transcribe", "https://api.openai.com/v1", "key"), byteArrayOf(1), "diary.m4a",
            ))
        }
    }

    @Test fun geminiPreservesAudioTranscriptionAndUsesItsOwnKeyHeader() = runBlocking {
        HttpClient(MockEngine { request ->
            assertEquals("/v1beta/models/gemini-2.5-flash:generateContent", request.url.encodedPath)
            assertEquals("gemini-key", request.headers["x-goog-api-key"])
            assertNull(request.headers[HttpHeaders.Authorization])
            respond("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"第一句\"},{\"text\":\"第二句\"}]}}]}", HttpStatusCode.OK)
        }).use { client ->
            assertEquals("第一句第二句", SpeechTranscriptionApi(client).transcribe(
                SpeechConfig("gemini", "gemini-2.5-flash", "https://generativelanguage.googleapis.com", "gemini-key"), byteArrayOf(1), "diary.m4a",
            ))
        }
    }

    @Test fun emptyAudioIsRejectedBeforeAnyNetworkRequest() = runBlocking {
        HttpClient(MockEngine { error("Empty audio must not be uploaded") }).use { client ->
            val error = assertFailsWith<SpeechTranscriptionException> {
                SpeechTranscriptionApi(client).transcribe(
                    SpeechConfig("minimax", "asr-1.0", "https://api.minimax.cn/v1", "key"), byteArrayOf(), "diary.m4a",
                )
            }
            assertEquals(TranscriptionErrorCode.AUDIO_EMPTY, error.code)
        }
    }

    @Test fun malformedResponsesArePermanentFailuresWithoutSensitiveServerContent() = runBlocking {
        for (body in listOf("key-echo-invalid-json", "{\"text\":null}", "{\"base_resp\":{\"status_code\":1000},\"text\":\"key-echo\"}")) {
            HttpClient(MockEngine { respond(body, HttpStatusCode.OK) }).use { client ->
                val error = assertFailsWith<SpeechTranscriptionException> {
                    SpeechTranscriptionApi(client).transcribe(
                        SpeechConfig("minimax", "asr-1.0", "https://api.minimax.cn/v1", "key"), byteArrayOf(1), "diary.m4a",
                    )
                }
                assertEquals(TranscriptionErrorCode.REQUEST_REJECTED, error.code)
                assertFalse(error.retryable)
                assertFalse(error.message.orEmpty().contains("key-echo"))
            }
        }
    }

    @Test fun miniMaxUsesItsOwnEndpointModelKeyAndJsonResponse() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("https://api.minimax.cn/v1/speech_to_text", request.url.toString())
            assertEquals("Bearer minimax-key", request.headers[HttpHeaders.Authorization])
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("asr-1.0"))
            assertTrue(body.contains("name=response_format") || body.contains("name=\"response_format\""))
            respond("{\"text\":\"今天很开心。\"}", HttpStatusCode.OK)
        }
        HttpClient(engine).use { client ->
            val config = SpeechConfig("minimax", "asr-1.0", "https://api.minimax.cn/v1", "minimax-key")
            assertEquals("今天很开心。", SpeechTranscriptionApi(client).transcribe(config, byteArrayOf(1, 2), "diary.m4a"))
        }
    }

    @Test fun siliconFlowUsesSelectedModelAndCompatibleEndpoint() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("/v1/audio/transcriptions", request.url.encodedPath)
            assertTrue(request.body.toByteArray().decodeToString().contains("FunAudioLLM/SenseVoiceSmall"))
            respond("{\"text\":\"转写结果\"}", HttpStatusCode.OK)
        }
        HttpClient(engine).use { client ->
            val config = SpeechConfig("siliconflow", "FunAudioLLM/SenseVoiceSmall", "https://api.siliconflow.cn/v1", "key")
            assertEquals("转写结果", SpeechTranscriptionApi(client).transcribe(config, byteArrayOf(1), "diary.m4a"))
        }
    }

    @Test fun qwenAudio31UsesNativeHttpAudioInputAndOutputText() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("/api/v1/services/aigc/multimodal-generation/generation", request.url.encodedPath)
            val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            assertEquals("qwen-audio-3.1-asr-flash", body["model"]!!.jsonPrimitive.content)
            val audio = body["input"]!!.jsonObject["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray[0].jsonObject
            assertEquals("input_audio", audio["type"]!!.jsonPrimitive.content)
            assertEquals("data:audio/mp4;base64,AQI=", audio["input_audio"]!!.jsonObject["data"]!!.jsonPrimitive.content)
            assertEquals("m4a", body["parameters"]!!.jsonObject["format"]!!.jsonPrimitive.content)
            respond("{\"output\":{\"text\":\"我的日记\"}}", HttpStatusCode.OK)
        }
        HttpClient(engine).use { client ->
            val config = SpeechConfig("dashscope", "qwen-audio-3.1-asr-flash", "https://dashscope.aliyuncs.com", "key")
            assertEquals("我的日记", SpeechTranscriptionApi(client).transcribe(config, byteArrayOf(1, 2), "diary.m4a"))
        }
    }

    @Test fun legacyQwenUsesChatAudioRequestInsteadOfWhisperEndpoint() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("/compatible-mode/v1/chat/completions", request.url.encodedPath)
            respond("{\"choices\":[{\"message\":{\"content\":\"旧模型转写\"}}]}", HttpStatusCode.OK)
        }
        HttpClient(engine).use { client ->
            val config = SpeechConfig("dashscope", "qwen3-asr-flash", "https://dashscope.aliyuncs.com", "key")
            assertEquals("旧模型转写", SpeechTranscriptionApi(client).transcribe(config, byteArrayOf(1), "diary.m4a"))
        }
    }

    @Test fun rejectionIsClassifiedWithoutPuttingServerEchoedKeysInErrors() = runBlocking {
        HttpClient(MockEngine { respond("invalid secret-key", HttpStatusCode.Unauthorized) }).use { client ->
            val error = assertFailsWith<SpeechTranscriptionException> {
                SpeechTranscriptionApi(client).transcribe(
                    SpeechConfig("openai", "gpt-transcribe", "https://api.openai.com/v1", "secret-key"), byteArrayOf(1), "diary.m4a",
                )
            }
            assertEquals(TranscriptionErrorCode.AUTH_FAILED, error.code)
            assertFalse(error.message.orEmpty().contains("secret-key"))
        }
    }
}

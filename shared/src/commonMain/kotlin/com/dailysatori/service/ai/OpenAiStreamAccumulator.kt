package com.dailysatori.service.ai

import kotlinx.serialization.json.*

/** Reassembles the same structured assistant message as a non-streaming response. No tool execution here. */
internal class OpenAiStreamAccumulator {
    private val text = StringBuilder()
    private val reasoning = StringBuilder()
    private val calls = mutableMapOf<Int, Call>()
    private var finished = false
    var done = false
        private set
    private var finishReason: String? = null
    var usage: JsonObject? = null
        private set
    private var size = 0

    fun accept(line: String): String? = try { acceptFrame(line) }
    catch (_: Exception) { throw IllegalArgumentException("Invalid AI stream frame") }

    private fun acceptFrame(line: String): String? {
        val data = line.trim().takeIf { it.startsWith("data:") }?.removePrefix("data:")?.trim() ?: return null
        if (data == "[DONE]") { finished = true; done = true; return null }
        if (data.isEmpty()) return null
        val root = Json.parseToJsonElement(data).jsonObject
        require(root["error"] == null) { "AI stream failed" }
        usage = root["usage"] as? JsonObject ?: usage
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let { finishReason = it; finished = true }
        val delta = choice["delta"]?.jsonObject ?: return null
        delta["reasoning_content"]?.jsonPrimitive?.contentOrNull?.let { append(reasoning, it) }
        delta["tool_calls"]?.jsonArray?.forEach { element ->
            val call = element.jsonObject
            val index = requireNotNull(call["index"]?.jsonPrimitive?.intOrNull)
            require(index in 0..15) { "Too many tool calls" }
            val target = calls.getOrPut(index) { Call() }
            call["id"]?.jsonPrimitive?.contentOrNull?.let { append(target.id, it) }
            val function = call["function"]?.jsonObject
            function?.get("name")?.jsonPrimitive?.contentOrNull?.let { append(target.name, it) }
            function?.get("arguments")?.jsonPrimitive?.contentOrNull?.let { append(target.arguments, it) }
        }
        return delta["content"]?.jsonPrimitive?.contentOrNull?.also { append(text, it) }
    }

    fun response(): JsonObject {
        require(finished && finishReason !in setOf("length", "content_filter", "error")) { "AI response was incomplete" }
        require(text.isNotBlank() || calls.isNotEmpty()) { "AI response was empty" }
        require(calls.values.map { it.id.toString() }.distinct().size == calls.size) { "Duplicate tool call identity" }
        require(calls.values.all { call -> runCatching { Json.parseToJsonElement(call.arguments.toString()) is JsonObject }.getOrDefault(false) }) {
            "Incomplete tool arguments"
        }
        return buildJsonObject {
            put("choices", buildJsonArray { add(buildJsonObject {
                finishReason?.let { put("finish_reason", it) }
                put("message", buildJsonObject {
                    put("role", "assistant"); put("content", text.toString())
                    if (reasoning.isNotEmpty()) put("reasoning_content", reasoning.toString())
                    if (calls.isNotEmpty()) put("tool_calls", buildJsonArray { calls.keys.sorted().forEach { index ->
                        val call = calls.getValue(index)
                        require(call.id.isNotBlank() && call.name.isNotBlank()) { "Incomplete tool call" }
                        add(buildJsonObject {
                            put("id", call.id.toString()); put("type", "function")
                            put("function", buildJsonObject { put("name", call.name.toString()); put("arguments", call.arguments.toString()) })
                        })
                    } })
                })
            }) })
            usage?.let { put("usage", it) }
        }
    }

    private fun append(target: StringBuilder, value: String) {
        size += value.length
        require(size <= 1_000_000) { "AI response exceeds the host budget" }
        target.append(value)
    }
    private class Call(val id: StringBuilder = StringBuilder(), val name: StringBuilder = StringBuilder(),
        val arguments: StringBuilder = StringBuilder())
}

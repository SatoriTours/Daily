package com.dailysatori.service.ai

import kotlin.test.*

class OpenAiStreamAccumulatorTest {
    @Test fun malformedOrProviderErrorFramesCannotBeSilentlySkipped() {
        for (bad in listOf("not-json-with-private-content", """{"error":{"message":"private-content"}}""")) {
            val stream = OpenAiStreamAccumulator()
            stream.accept("""data: {"choices":[{"delta":{"content":"unfinished"}}]}""")
            val error = assertFailsWith<IllegalArgumentException> { stream.accept("data: $bad") }
            assertFalse(error.message.orEmpty().contains("private-content"))
        }
    }

    @Test fun interruptedAndIncompleteToolArgumentsCannotBecomeACompletedResponse() {
        val interrupted = OpenAiStreamAccumulator()
        interrupted.accept("""data: {"choices":[{"delta":{"content":"unfinished"}}]}""")
        assertFailsWith<IllegalArgumentException> { interrupted.response() }
        val invalid = OpenAiStreamAccumulator()
        invalid.accept("""data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"a","function":{"name":"read_record","arguments":"{"}}]},"finish_reason":"tool_calls"}]}""")
        assertFailsWith<IllegalArgumentException> { invalid.response() }
    }

    @Test fun lengthAndContentFilterStopsRemainFailuresEvenAfterDone() {
        for (reason in listOf("length", "content_filter", "error")) {
            val stream = OpenAiStreamAccumulator()
            stream.accept("""data: {"choices":[{"delta":{"content":"not a final answer"},"finish_reason":"$reason"}]}""")
            stream.accept("data: [DONE]")
            assertFailsWith<IllegalArgumentException> { stream.response() }
        }
    }
}

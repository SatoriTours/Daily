package com.dailysatori.ui.feature.aiconfig

import kotlin.test.Test
import kotlin.test.assertEquals

class AiPurposeFormatHostTest {

    @Test
    fun testExtractsStandardHost() {
        assertEquals("api.deepseek.com", formatApiHost("https://api.deepseek.com/v1"))
        assertEquals("api.openai.com", formatApiHost("https://api.openai.com/v1/chat/completions"))
        assertEquals("dashscope.aliyuncs.com", formatApiHost("dashscope.aliyuncs.com"))
    }

    @Test
    fun testRetainsCustomPort() {
        assertEquals("localhost:11434", formatApiHost("http://localhost:11434/v1"))
        assertEquals("192.168.1.100:8080", formatApiHost("http://192.168.1.100:8080"))
    }

    @Test
    fun testNeverLeaksCredentialsOrQueryParams() {
        val sensitiveUrl = "https://admin:super_secret_password@proxy.internal.corp:8443/api/v1?token=secret123&user=john"
        val formatted = formatApiHost(sensitiveUrl)
        assertEquals("proxy.internal.corp:8443", formatted)
    }

    @Test
    fun invalidAddressNeverFallsBackToDisplayingTheRawInput() {
        assertEquals("", formatApiHost("secret-token!"))
        assertEquals("", formatApiHost("https://invalid host?token=secret"))
    }

    @Test
    fun testHandlesEmptyOrBlank() {
        assertEquals("", formatApiHost(""))
        assertEquals("", formatApiHost("   "))
    }
}

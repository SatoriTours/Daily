package com.dailysatori.service.mcp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivacyMaskerTest {
    @Test
    fun masksSensitiveDiaryTextAndRestoresAnswer() {
        val masker = PrivacyMasker()
        val masked = masker.mask("我叫张三，身份证号110101199003071234，密码是 abc123，api_key sk-test-secret")

        assertFalse(masked.contains("张三"))
        assertFalse(masked.contains("110101199003071234"))
        assertFalse(masked.contains("abc123"))
        assertFalse(masked.contains("sk-test-secret"))
        assertTrue(masked.contains("[PERSON_1]"))
        assertTrue(masked.contains("[ID_1]"))
        assertTrue(masked.contains("[SECRET_1]"))

        val restored = masker.restore("[PERSON_1] 的证件是 [ID_1]，密码相关为 [SECRET_1]")
        assertTrue(restored.contains("张三"))
        assertTrue(restored.contains("110101199003071234"))
    }
}

package com.dailysatori.service.sms

import kotlin.test.*
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

class SmsPrivacyTest {
    @Test fun verificationMessagesNeverBecomeAiInput() {
        listOf("验证码123456，请于24小时内完成充值", "Your OTP is 123456. Pay within 24 hours", "Your verification code is １２３４５６", "Use 123456 to sign in", "动态口令：一二三四五六").forEach {
            assertNull(SmsPrivacy.aiText(it))
        }
    }

    @Test fun financialAndIdentityValuesAreRemovedBeforeAi() {
        val result = assertNotNull(SmsPrivacy.aiText("账号AB123XY，卡号6222 1234 5678 9999，余额人民币1,234.56元，请24小时内缴费。电话+64 21 123 4567，email me@example.com https://bank.example/pay?token=secret"))
        listOf("AB123XY", "6222", "9999", "1,234.56", "1234", "+64", "me@example.com", "token=secret").forEach { assertFalse(result.contains(it), it) }
        assertFalse(result.any(Char::isDigit))
        assertTrue(result.contains("缴费"))
    }

    @Test fun noNumberIsTrustedJustBecauseItIsShortOrLooksLikeADate() {
        val text = assertNotNull(SmsPrivacy.aiText("Balance $5.50; account 2026-10-05; repay within 24 hours"))
        assertFalse(text.any(Char::isDigit))
        assertFalse(text.contains("2026-10-05"))
    }

    @Test fun fullWidthAndArabicIndicDigitsAreRemoved() {
        val text = assertNotNull(SmsPrivacy.aiText("余额５００元，卡号١٢٣٤٥٦٧٨，明天缴费"))
        assertFalse(text.any(Char::isDigit))
    }

    @Test fun unsupportedSensitiveIdentityMessagesStayLocal() {
        assertNull(SmsPrivacy.aiText("姓名张三，住址海淀区某路，请明天缴费"))
        assertNull(SmsPrivacy.aiText("Dear John Smith, your account needs a payment tomorrow"))
    }

    @Test fun balancesWrittenWithoutDigitsAreAlsoRedacted() {
        listOf("余额为五百元。请在24小时内充值", "Balance five dollars; please top up within 24 hours", "可用余额：壹仟伍佰元，明天缴费").forEach {
            val text = assertNotNull(SmsPrivacy.aiText(it))
            listOf("五百", "five dollars", "壹仟伍佰").forEach { sensitive -> assertFalse(text.contains(sensitive)) }
        }
    }

    @Test fun amountsWithNoBalanceLabelStillCannotLeak() {
        listOf("请在24小时内支付五百元", "Please pay five dollars within 24 hours", "请24小时内缴纳壹仟伍佰圆").forEach {
            val text = assertNotNull(SmsPrivacy.aiText(it))
            listOf("五百", "five dollars", "壹仟伍佰").forEach { value -> assertFalse(text.contains(value)) }
        }
    }

    @Test fun spelledNumbersAndChineseAccountDigitsAreMasked() {
        val result = assertNotNull(SmsPrivacy.aiText("Please pay five bucks within 24 hours. 账号一二三四五六，缴费五百点五元。"))
        listOf("five", "一二三四五六", "五百", "五元").forEach { assertFalse(result.contains(it)) }
    }

    @Test fun separatedVerificationLabelAndUnsupportedSensitiveScriptsStayLocal() {
        assertNull(SmsPrivacy.aiText("验\u200B证\u200B码 123456 24小时内充值"))
        assertNull(SmsPrivacy.aiText("Your confirmation code 123456 is valid for 24 hours"))
    }

    @Test fun timeLimitUsesReceiptInstantAcrossDaysAndDst() {
        val received = Instant.parse("2026-10-24T23:30:00Z")
        val candidates = SmsDeadlineExtractor.extract("Top-Up within the next 24 hours or your credit will expire", received, TimeZone.of("Europe/London"))
        assertEquals(listOf(Instant.parse("2026-10-25T23:30:00Z")), candidates.map { it.at })
    }

    @Test fun chineseTimeLimitAndExplicitDateAreSupported() {
        val received = Instant.parse("2026-10-05T15:00:00Z")
        assertEquals(Instant.parse("2026-10-06T15:00:00Z"), SmsDeadlineExtractor.extract("请在24小时内充值", received, TimeZone.UTC).single().at)
        assertEquals(Instant.parse("2026-10-06T17:30:00Z"), SmsDeadlineExtractor.extract("请于2026-10-06 17:30前取件", received, TimeZone.UTC).single().at)
        assertTrue(SmsDeadlineExtractor.extract("明天充值", received, TimeZone.UTC).isEmpty())
    }

    @Test fun anAccountNumberIsNotDeadlineEvidence() {
        assertTrue(SmsDeadlineExtractor.extract("账号2026-10-06 17:30，请及时缴费", Instant.parse("2026-10-05T15:00:00Z"), TimeZone.UTC).isEmpty())
    }
}

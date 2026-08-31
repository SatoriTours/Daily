package com.dailysatori.service.reminder

import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderInputSplitterTest {
    @Test fun keepsSentenceQualifierWithItsReminder() {
        assertEquals(
            listOf("9月2日提醒我还款。工作时间不要响"),
            splitReminderInput("9月2日提醒我还款。工作时间不要响").map { it.text },
        )
    }

    @Test fun splitsCommonRelativeReminderSentences() {
        assertEquals(
            listOf("今晚提醒我充值", "下周提醒我还信用卡"),
            splitReminderInput("今晚提醒我充值。下周提醒我还信用卡").map { it.text },
        )
    }
    @Test fun splitsLinesSemicolonsAndNumberedItemsWithoutSplittingCommaQualifiers() {
        val input = "1. 9月2日提醒我还信用卡，工作时间静音\n2. 9月5日提醒我充值；每年12月20日提醒我续订域名"

        assertEquals(
            listOf("9月2日提醒我还信用卡，工作时间静音", "9月5日提醒我充值", "每年12月20日提醒我续订域名"),
            splitReminderInput(input).map { it.text },
        )
    }

    @Test fun removesEmptyAndExactDuplicateFragmentsWhileKeepingFirstIndexOrder() {
        assertEquals(
            listOf(0 to "9月2日提醒我还信用卡", 2 to "9月5日提醒我充值"),
            splitReminderInput("9月2日提醒我还信用卡；；9月5日提醒我充值；9月2日提醒我还信用卡")
                .map { it.index to it.text },
        )
    }

    @Test fun removesFragmentsContainingOnlyWhitespaceAndPunctuation() {
        assertEquals(emptyList(), splitReminderInput("  ，,。！？!?…；; \n  "))
    }

    @Test fun splitsConsecutiveReminderStatementsAtSentenceBoundaries() {
        assertEquals(
            listOf("9月2日提醒我还款", "9月5日提醒我充值"),
            splitReminderInput("9月2日提醒我还款。9月5日提醒我充值。。").map { it.text },
        )
    }

    @Test fun splitsNumberedItemsWithoutNewlines() {
        assertEquals(
            listOf("9月2日提醒我还款", "9月5日提醒我充值", "每年12月20日提醒我续订域名"),
            splitReminderInput("1. 9月2日提醒我还款 2. 9月5日提醒我充值 3、每年12月20日提醒我续订域名")
                .map { it.text },
        )
    }
}

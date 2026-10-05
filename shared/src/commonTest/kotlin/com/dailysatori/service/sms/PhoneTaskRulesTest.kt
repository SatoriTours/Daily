package com.dailysatori.service.sms

import kotlin.test.*

class PhoneTaskRulesTest {
    @Test fun completedPaymentDoesNotHideAnUnfinishedAction() {
        assertTrue(SmsPrivacy.isCandidate("已付款100元，请周五取件"))
        assertTrue(SmsPrivacy.isCandidate("支付成功，请明天领取商品"))
        assertEquals(listOf("请周五取货"), SmsPrivacy.taskClauses("已支付订金100元，请周五取货"))
    }

    @Test fun processingAndCompletedTransactionsAreNotPaymentTodos() {
        listOf("支付处理中56元", "待确认收款56元", "消费56元", "支付成功56元").forEach {
            assertFalse(SmsPrivacy.isCandidate(it), it)
        }
    }

    @Test fun merchantAndRepaymentDateDoNotBecomeTasks() {
        assertFalse(SmsPrivacy.isCandidate("消费56元，商户：取件中心，信用卡还款日为每月20日"))
    }
}

package com.dailysatori.core.bookkeeping

import kotlin.test.*

class FinancialNotificationTest {
    @Test fun acceptsBankTransactionAndSkipsGroupSummary() {
        assertEquals("银行\n消费人民币56元", financialNotificationText("bank", "银行", "消费人民币56元", false))
        assertNull(financialNotificationText("bank", "银行", "消费人民币56元", true))
    }

    @Test fun wechatAndAlipayChatMessagesAreNotPaymentSources() {
        assertNull(financialNotificationText("com.tencent.mm", "朋友", "消费人民币56元", false))
        assertNull(financialNotificationText("com.eg.android.AlipayGphone", "朋友", "支付成功56元", false))
        assertNotNull(financialNotificationText("com.tencent.mm", "微信支付", "支付成功56元", false))
        assertNotNull(financialNotificationText("com.eg.android.AlipayGphone", "支付助手", "支付成功56元", false))
    }

    @Test fun emptyHiddenOrOversizedBodyCannotGenerateAnEntry() {
        assertNull(financialNotificationText("bank", "消费成功56元", "", false))
        assertNull(financialNotificationText("bank", "银行", "x".repeat(8001), false))
    }
}

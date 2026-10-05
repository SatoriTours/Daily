package com.dailysatori.core.bookkeeping

import kotlin.test.*

class PhoneNotificationTest {
    @Test fun serviceTasksAreAcceptedAlongWithPayments() {
        assertEquals("取件提醒\n请于明天取件", phoneNotificationText("parcel", "取件提醒", "请于明天取件", false, false, "self"))
        assertNotNull(phoneNotificationText("com.eg.android.AlipayGphone", "预约提醒", "请明天到店", false, false, "self"))
    }
    @Test fun ownMessagesSummariesOngoingAndChatAreExcluded() {
        assertNull(phoneNotificationText("self", "缴费", "请缴费", false, false, "self"))
        assertNull(phoneNotificationText("bank", "缴费", "请缴费", true, false, "self"))
        assertNull(phoneNotificationText("bank", "缴费", "请缴费", false, true, "self"))
        assertNull(phoneNotificationText("com.tencent.mm", "朋友", "请明天取件", false, false, "self"))
    }
}

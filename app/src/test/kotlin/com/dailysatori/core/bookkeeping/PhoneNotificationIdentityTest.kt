package com.dailysatori.core.bookkeeping

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class PhoneNotificationIdentityTest {
    @Test fun changingNotificationTimestampDoesNotCreateANewEvent() = withSettings { settings ->
        val identity = PhoneNotificationIdentity(settings)
        val first = identity.key("bank:notification", 1000)
        assertEquals(first, identity.key("bank:notification", 2000))
    }

    @Test fun identitySurvivesListenerRecreation() = withSettings { settings ->
        val first = PhoneNotificationIdentity(settings).key("bank:notification", 1000)
        assertEquals(first, PhoneNotificationIdentity(settings).key("bank:notification", 3000))
    }

    @Test fun repostAfterRemovalIsIndependentEvenWithTheSameTimestamp() = withSettings { settings ->
        val identity = PhoneNotificationIdentity(settings)
        val first = identity.key("bank:notification", 1000)
        val other = identity.key("parcel:notification", 1000)
        identity.removed("bank:notification")
        assertNotEquals(first, identity.key("bank:notification", 1000))
        assertEquals(other, identity.key("parcel:notification", 2000))
    }

    @Test fun reconnectPreservesActiveSessionsAndDropsRemovedSessions() = withSettings { settings ->
        val first = PhoneNotificationIdentity(settings)
        val active = first.key("active", 1000)
        val closed = first.key("closed", 1000)
        val recreated = PhoneNotificationIdentity(settings)
        recreated.reconcile(mapOf("active" to 2000))
        assertEquals(active, recreated.key("active", 2000))
        assertNotEquals(closed, recreated.key("closed", 1000))
        assertFalse(settings.get("phone_assistant.notification_sessions")!!.contains("active"))
    }

    @Test fun firstConnectionAdoptsExistingNotificationIdentity() = withSettings { settings ->
        val identity = PhoneNotificationIdentity(settings)
        identity.reconcile(mapOf("bank:notification" to 1000))
        assertEquals("fe24057c51fe155209112f4f56bff155866f8a221b5782c597f0aa4a09ae88f3", identity.key("bank:notification", 1000))
    }

    private fun withSettings(block: (SettingRepository) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            block(SettingRepository(DailySatoriDatabase(driver)))
        } finally { driver.close() }
    }
}

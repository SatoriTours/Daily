package com.dailysatori.data.repository

import com.dailysatori.shared.db.DailySatoriDatabase

class SessionRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getBySessionId(sessionId: String) =
        q.selectSessionBySessionId(sessionId).executeAsOneOrNull()

    fun insert(sessionId: String, username: String? = null) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertSession(sessionId, 1, username, now, now, now)
    }

    fun touch(sessionId: String) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateSessionAccess(now, sessionId)
    }

    fun delete(sessionId: String) = q.deleteSession(sessionId)

    fun deleteExpired(beforeMs: Long) = q.deleteExpiredSessions(beforeMs)
}

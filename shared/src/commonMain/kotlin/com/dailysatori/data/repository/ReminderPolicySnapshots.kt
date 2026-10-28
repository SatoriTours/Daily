package com.dailysatori.data.repository

import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.service.externalfavorites.sha256Hex
import kotlinx.datetime.Clock

/** Immutable raw payloads. Hash is an index hint, never proof of equality. */
internal class ReminderPolicySnapshots(
    private val db:DailySatoriDatabase,
    private val digest:(String)->String = ::sha256Hex,
) {
    fun intern(payload:String,format:Long=1,createdAt:Long=Clock.System.now().toEpochMilliseconds()):String =
        db.dailySatoriQueries.transactionWithResult {
            val q=db.dailySatoriQueries
            val hash=digest(payload)
            q.selectReminderPoliciesByHash(format,hash).executeAsList()
                .firstOrNull { it.payload_json==payload }?.let { return@transactionWithResult it.id }
            val prefix="policy:$format:$hash"
            val id=(0..q.countReminderPolicies().executeAsOne()+1)
                .asSequence().map { if(it==0L) prefix else "$prefix:$it" }
                .first { q.selectReminderPolicyById(it).executeAsOneOrNull()==null }
            q.insertReminderPolicy(id,format,payload,hash,createdAt)
            id
        }
}

package com.dailysatori.service.reminder

import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ReminderScheduleIntent(val reminderId:String,val version:Long)

/** Same database transaction as the domain mutation; wakeup happens only after commit. */
internal class ReminderScheduleOutbox(private val db:DailySatoriDatabase) {
    fun enqueue(id:String,version:Long)=AsyncTaskRepository(db).enqueue(
        TYPE,Json.encodeToString(ReminderScheduleIntent(id,version)),uniqueKey="$TYPE:$id:$version",
    )
    companion object { const val TYPE="reminder_schedule" }
}

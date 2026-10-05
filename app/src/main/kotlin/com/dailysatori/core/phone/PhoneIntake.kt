package com.dailysatori.core.phone

import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.service.phone.PhoneAssistantService
import com.dailysatori.service.phone.PhoneEvent

class PhoneIntake(private val service: PhoneAssistantService, private val scheduler: AsyncTaskScheduler) {
    suspend fun accept(event: PhoneEvent): Boolean {
        val task = service.accept(event) ?: return false
        scheduler.enqueue(task)
        return true
    }
}

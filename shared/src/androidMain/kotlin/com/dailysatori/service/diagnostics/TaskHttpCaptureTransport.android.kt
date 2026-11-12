package com.dailysatori.service.diagnostics

import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.CoroutineContext

/**
 * JVM bridge so the OkHttp transport layer sees the same capture handle as the business read points:
 * one call has exactly one identity instead of a second transport-only id.
 */
private val captureHandleSlot = ThreadLocal<TaskHttpRequestHandle?>()

internal fun currentCaptureHandleForTransport(): TaskHttpRequestHandle? = captureHandleSlot.get()

internal fun captureHandleContext(handle: TaskHttpRequestHandle): CoroutineContext = captureHandleSlot.asContextElement(handle)

private fun ThreadLocal<TaskHttpRequestHandle?>.asContextElement(value: TaskHttpRequestHandle): CoroutineContext =
    object : ThreadContextElement<TaskHttpRequestHandle?> {
        override val key: CoroutineContext.Key<*> = TaskHttpRequestHandle.Key
        override fun updateThreadContext(context: CoroutineContext): TaskHttpRequestHandle? {
            val previous = get()
            set(value)
            return previous
        }
        override fun restoreThreadContext(context: CoroutineContext, oldState: TaskHttpRequestHandle?) {
            set(oldState)
        }
    }

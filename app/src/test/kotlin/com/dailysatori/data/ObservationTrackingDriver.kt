package com.dailysatori.data

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.SqlDriver
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class ObservationTrackingDriver(private val delegate: SqlDriver) : SqlDriver by delegate {
    private val listeners = ConcurrentHashMap<Query.Listener, Set<String>>()
    private val subscriptions = ConcurrentHashMap<String, AtomicInteger>()

    fun activeSubscriptions(table: String): Int = listeners.values.count { table in it }
    fun totalSubscriptions(table: String): Int = subscriptions[table]?.get() ?: 0

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        listeners[listener] = queryKeys.toSet()
        queryKeys.forEach { subscriptions.computeIfAbsent(it) { AtomicInteger() }.incrementAndGet() }
        delegate.addListener(*queryKeys, listener = listener)
    }

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        delegate.removeListener(*queryKeys, listener = listener)
        listeners.remove(listener)
    }
}

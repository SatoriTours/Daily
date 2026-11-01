package com.dailysatori.service.assistant

import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.ai.AiHttpFailure
import com.dailysatori.service.ai.aiChatRequestTimeoutMillis
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock

internal class AssistantRequestBudgetExceeded : IllegalStateException(
    "本次输入已达到 6 次 AI 请求上限，已停止自动重试；原文及已执行的动作已保留",
)

/** Separate from the discardable graph: retries and privacy-driven graph rebuilds share the budget. */
internal class AssistantRequestBudget(
    driver: SqlDriver, private val turn: AssistantTurn, private val owner: String,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val store = AssistantStore(driver)
    private val turns = AssistantTurnRepository(driver)
    private val settings = SettingRepository(DailySatoriDatabase(driver))
    private val key = "assistant.ai.requests.${turn.id}"
    private var requestDeadline = Long.MAX_VALUE

    fun beforeSend() = store.transaction {
        if (!turns.canExecute(turns.get(turn.id) ?: throw CancellationException("输入不存在"), owner))
            throw CancellationException("助手任务已失权")
        val saved = settings.get(key)
        val count = if (saved == null) 0 else saved.toIntOrNull() ?: 6
        if (count !in 0..5) throw AssistantRequestBudgetExceeded()
        settings.upsert(key, (count + 1).toString())
        requestDeadline = nowMs() + aiChatRequestTimeoutMillis()
    }

    fun checkDeadline() {
        if (nowMs() >= requestDeadline) throw AiHttpFailure(408)
    }
}

package com.dailysatori.service.assistant

import app.cash.sqldelight.db.SqlDriver
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AssistantActionRequest(
    val kind: String, val sourceQuote: String,
    val urlRef: String? = null, val content: String = "",
    val startDate: String? = null, val endDate: String? = null, val time: String? = null,
    val recurrence: String = "once", val activeDays: String = "daily", val deadlineAt: String? = null,
)

/** One immutable, host-numbered plan per turn. Retries never let the model invent replacement writes. */
class AssistantActionService(driver: SqlDriver, private val business: RepositoryAssistantBusiness) {
    private val store = AssistantStore(driver)
    private val turns = AssistantTurnRepository(driver)
    private val data = com.dailysatori.service.assistant.data.AssistantDataGateway(driver)

    fun submit(turn: AssistantTurn, owner: String, requests: List<AssistantActionRequest>): List<AssistantActionView> {
        require(requests.isNotEmpty() && requests.size <= 8)
        val input = requireNotNull(turns.input(turn))
        val safeInput = modelInput(input)
        requests.forEach { request ->
            require(request.kind in setOf("article", "reminder"))
            require(request.sourceQuote.isNotBlank() && safeInput.contains(request.sourceQuote)) { "source_quote_must_match_current_input" }
            require(!isQuotedAssistantEvidence(safeInput, request.sourceQuote)) { "quoted_content_is_not_instruction" }
            require(!hasNegatedAssistantAction(safeInput, request.sourceQuote, request.kind)) { "negated_instruction" }
            if (request.kind == "article") {
                require(request.urlRef in inputLinks(input) && request.sourceQuote.contains("[${request.urlRef}]")) { "article_quote_must_contain_input_link_handle" }
                val at = safeInput.indexOf("[${request.urlRef}]")
                val nearby = safeInput.substring((at - 100).coerceAtLeast(0), (at + 100).coerceAtMost(safeInput.length))
                require(!Regex("(不要|不用|无需|不必|不|别).{0,4}(保存|收录|收藏)|仅.{0,3}(询问|解释|分析)|do not save|don't save", RegexOption.IGNORE_CASE).containsMatchIn(nearby))
            } else {
                require(Regex("提醒我|待办|闹钟|remind me|todo", RegexOption.IGNORE_CASE).containsMatchIn(request.sourceQuote)) { "reminder_quote_must_contain_instruction" }
                require(!Regex("(不要|不用|无需|不必|别).{0,3}(提醒|待办|闹钟)|don't remind|do not remind", RegexOption.IGNORE_CASE).containsMatchIn(request.sourceQuote))
            }
        }
        val encoded = Json.encodeToString(requests)
        require(data.redact(encoded) == encoded) { "credential_bearing_action" }
        store.transaction {
            require(turns.canExecute(requireNotNull(turns.get(turn.id)), owner))
            val previous = store.text("SELECT action_plan_json FROM assistant_turn WHERE id = ?", turn.id).orEmpty()
            require(previous.isBlank() || previous == encoded) { "Plan already frozen; use the existing action outcomes" }
            if (previous.isBlank()) {
                store.exec("UPDATE assistant_turn SET action_plan_json = ? WHERE id = ?", encoded, turn.id)
                requests.forEachIndexed { index, request ->
                    store.exec("INSERT INTO assistant_action(id,turn_id,kind,parameters_json,fingerprint,status) VALUES (?,?,?,?,?, 'planned')",
                        "${turn.id}:$index", turn.id, request.kind, Json.encodeToString(request), com.dailysatori.service.externalfavorites.sha256Hex(Json.encodeToString(request)))
                }
            }
        }
        store.changed()
        return resume(turn, owner)
    }

    fun resume(turn: AssistantTurn, owner: String): List<AssistantActionView> {
        for (action in stored(turn.id)) {
            require(turns.canExecute(requireNotNull(turns.get(turn.id)), owner))
            if (action.status in setOf("cancelled", "needs_input", "succeeded")) continue
            var current = action
            if (current.targetId == null) {
                try {
                    store.transaction {
                        require(turns.canExecute(requireNotNull(turns.get(turn.id)), owner))
                        val raw = requireNotNull(store.text("SELECT parameters_json FROM assistant_action WHERE id = ?", action.id))
                        current = business.prepare(action.id, turn, Json.decodeFromString(raw), requireNotNull(turns.input(turn)))
                        store.exec("UPDATE assistant_action SET status=?, target_id=?, task_id=?, error_code='' WHERE id=?",
                            current.status, current.targetId, current.taskId, action.id)
                    }
                } catch (_: Exception) {
                    store.transaction {
                        if (turns.canExecute(requireNotNull(turns.get(turn.id)), owner))
                            store.exec("UPDATE assistant_action SET status='needs_input', error_code='invalid_or_incomplete_action' WHERE id=?", action.id)
                    }
                    continue
                }
            }
            if (current.status == "succeeded") continue
            try {
                require(turns.canExecute(requireNotNull(turns.get(turn.id)), owner))
                business.dispatch(current)
                store.transaction {
                    if (turns.canExecute(requireNotNull(turns.get(turn.id)), owner)) store.exec(
                        "UPDATE assistant_action SET status=?, error_code='' WHERE id=?",
                        if (current.kind == "reminder") "succeeded" else "submitted", current.id)
                }
            } catch (_: Exception) {
                store.transaction {
                    if (turns.canExecute(requireNotNull(turns.get(turn.id)), owner)) store.exec(
                        "UPDATE assistant_action SET error_code='schedule_pending' WHERE id=?", current.id)
                }
            }
        }
        store.changed()
        return list(turn.id)
    }

    /** A clarification can amend only an unsubmitted action in the same conversation. */
    fun clarify(turn: AssistantTurn, owner: String, actionId: String, replacement: AssistantActionRequest): List<AssistantActionView> {
        val originId = actionId.substringBefore(':').toLong()
        val origin = requireNotNull(turns.get(originId))
        require(origin.sessionId == turn.sessionId && replacement.sourceQuote.isNotBlank() &&
            modelInput(requireNotNull(turns.input(turn))).contains(replacement.sourceQuote))
        require(!isQuotedAssistantEvidence(modelInput(requireNotNull(turns.input(turn))), replacement.sourceQuote))
        require(!hasNegatedAssistantAction(modelInput(requireNotNull(turns.input(turn))), replacement.sourceQuote, replacement.kind))
        store.transaction {
            require(turns.canExecute(requireNotNull(turns.get(turn.id)), owner))
            val action = stored(originId).first { it.id == actionId }
            require(action.kind == replacement.kind && ((action.status == "needs_input" && action.targetId == null) || business.canRefine(action)))
            if (action.targetId != null) business.refine(action, replacement)
            // Move the unresolved intent to the clarifying turn; stable business action ID remains unchanged.
            store.exec("UPDATE assistant_action SET turn_id=?, parameters_json=?, status='planned', error_code='' WHERE id=?",
                turn.id, Json.encodeToString(replacement), actionId)
            store.exec("UPDATE assistant_action SET status=? WHERE id=?", if (action.targetId == null) "planned" else "submitted", actionId)
        }
        return resume(turn, owner)
    }

    fun list(turnId: Long): List<AssistantActionView> = stored(turnId).map { action ->
        business.current(action).let { view -> if (action.message == "schedule_pending" && view.status != "failed") view.copy(
            status = "schedule_pending", message = "已保存，通知/后台调度尚未确认；重试只补调度，不重复创建",
        ) else view }
    }

    private fun stored(turnId: Long): List<AssistantActionView> = store.rows(
        "SELECT id,kind,status,target_id,task_id,error_code FROM assistant_action WHERE turn_id=? ORDER BY id", turnId,
    ) { c -> AssistantActionView(c.getString(0)!!, c.getString(1)!!, c.getString(2)!!, c.getString(3), c.getLong(4), c.getString(5).orEmpty()) }
}

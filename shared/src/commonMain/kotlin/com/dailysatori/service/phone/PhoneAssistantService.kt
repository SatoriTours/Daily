package com.dailysatori.service.phone

import com.dailysatori.bookkeeping.*
import com.dailysatori.data.repository.*
import com.dailysatori.service.bookkeeping.LedgerRules
import com.dailysatori.service.sms.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.*

class PhoneAssistantService(
    private val messages: PhoneMessageRepository, private val smsSources: SmsSourceRepository,
    private val sms: SmsReminderService, private val ledger: BookkeepingRepository,
    private val settings: SettingRepository, private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()
    private val policies = PhonePolicies(settings)
    private val parser = TransactionParser()
    fun preferences() = policies.preferences()
    suspend fun configure(channel: PhoneChannel, options: PhoneOptions) = mutex.withLock {
        messages.transaction { policies.configure(channel, options) }
    }
    suspend fun selectSource(source: String, selected: Boolean) = mutex.withLock {
        messages.transaction { policies.selectSource(source, selected) }
    }

    suspend fun accept(event: PhoneEvent): Long? = mutex.withLock {
        if (!policies.accepts(event) || SmsPrivacy.isVerification(event.text)) return@withLock null
        if (event.channel == PhoneChannel.SMS && event.origin.trim().lowercase() in smsSources.blockedSenders()) return@withLock null
        val old = messages.get(event.id)
        if (old?.textErased == true || old?.event?.text == event.text) return@withLock null
        val options = policies.optionsFor(event)
        val revision = (old?.revision ?: 0) + 1
        val tasks = if (options.todos) SmsPrivacy.taskClauses(event.text) else emptyList()
        val matched = mutableSetOf<String>()
        val planned = tasks.mapIndexed { index, text ->
            (old?.todos?.firstOrNull { it.id !in matched && (it.text == text ||
                it.state in setOf(PhoneResultState.DONE, PhoneResultState.IGNORED) && canonicalTodo(it.text) == canonicalTodo(text)) }
                ?: planTodo(event, revision, index, text)).also { matched += it.id }
        }
        val todos = (planned + old?.todos.orEmpty().filter { it.state in setOf(PhoneResultState.DONE, PhoneResultState.IGNORED) }).distinctBy { it.id }
        val hasLedger = options.ledger && parser.parse(event.text) != null
        val ledgerState = when {
            old?.ledgerState in setOf(PhoneResultState.DONE, PhoneResultState.IGNORED) -> old!!.ledgerState
            hasLedger -> PhoneResultState.QUEUED
            else -> old?.ledgerState ?: PhoneResultState.SKIPPED
        }
        if (todos.isEmpty() && ledgerState == PhoneResultState.SKIPPED) return@withLock null
        val record = PhoneMessage(event, revision, options.generation, todos, ledgerState, old?.ledgerId.orEmpty())
        messages.transaction {
            old?.todos?.filter { prior -> todos.none { it.id == prior.id } && prior.state != PhoneResultState.DONE }
                ?.forEach { sms.ignore(it.id) }
            todos.filter { it.state in setOf(PhoneResultState.QUEUED, PhoneResultState.PENDING) }.forEach {
                sms.stageLocal(it.id, SmsSource(event.origin, it.text), instant(event), zone(event), localDraft(event, it.text))
            }
            messages.save(record)
            messages.enqueue(record)
        }
    }

    suspend fun process(id: String): PhoneMessage? {
        val start = mutex.withLock {
            val row = messages.get(id) ?: return null
            if (!active(row)) return row
            processLedger(row)
        }
        for (todo in start.todos.filter { it.state == PhoneResultState.QUEUED }) processTodo(start, todo)
        return messages.get(id)
    }

    private fun processLedger(row: PhoneMessage): PhoneMessage {
        if (!policies.optionsFor(row.event).ledger || row.ledgerState != PhoneResultState.QUEUED) return row
        return try {
            messages.transaction {
                val state = ledger.ingest(source(row), row.id, row.event.text, row.event.receivedAt, LedgerRules.of(settings))
                val entry = state.entries.firstOrNull { it.source == source(row) && row.id in it.eventKeys }
                val next = row.copy(ledgerId = entry?.id.orEmpty(), ledgerState = when (entry?.status) {
                    LedgerStatus.POSTED -> PhoneResultState.DONE
                    LedgerStatus.PENDING -> PhoneResultState.PENDING
                    LedgerStatus.IGNORED, LedgerStatus.DELETED -> PhoneResultState.IGNORED
                    null -> PhoneResultState.SKIPPED
                }, ledgerReason = entry?.reason.orEmpty())
                messages.save(next); next
            }
        } catch (_: Exception) {
            row.copy(ledgerState = PhoneResultState.FAILED, ledgerReason = "processing_failed").also(messages::save)
        }
    }

    private suspend fun processTodo(snapshot: PhoneMessage, todo: PhoneTodo) {
        try {
            var draft: SmsReminderDraft? = localDraft(snapshot.event, todo.text)
            val cloud = mutex.withLock {
                if (!active(snapshot) || !policies.optionsFor(snapshot.event).todos) return
                policies.optionsFor(snapshot.event).cloud
            }
            if (cloud && SmsAiInput.from(SmsSource(snapshot.event.origin, todo.text), instant(snapshot.event), zone(snapshot.event)) != null)
                draft = sms.analyzeDraft(SmsSource(snapshot.event.origin, todo.text), instant(snapshot.event), zone(snapshot.event))
            mutex.withLock {
                val current = messages.get(snapshot.id) ?: return
                if (!sameActive(current, snapshot) || !policies.optionsFor(current.event).todos ||
                    current.todos.firstOrNull { it.id == todo.id }?.state != PhoneResultState.QUEUED) return
                messages.transaction {
                    val reminderId = draft?.let { sms.stageLocal(todo.id, SmsSource(current.event.origin, todo.text), instant(current.event), zone(current.event), it); sms.confirm(todo.id, it, checkDuplicates = false) }
                    replaceTodo(current, todo.copy(state = if (reminderId == null) PhoneResultState.IGNORED else PhoneResultState.DONE,
                        reminderId = reminderId.orEmpty()))
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            mutex.withLock {
                val current = messages.get(snapshot.id) ?: return
                if (sameActive(current, snapshot) && current.todos.firstOrNull { it.id == todo.id }?.state == PhoneResultState.QUEUED)
                    replaceTodo(current, todo.copy(state = PhoneResultState.FAILED, reason = "processing_failed"))
            }
        }
    }

    suspend fun confirmTodo(id: String, todoId: String, draft: SmsReminderDraft? = null): String? = mutex.withLock {
        val row = requireNotNull(messages.get(id))
        val todo = row.todos.single { it.id == todoId }
        if (todo.state == PhoneResultState.DONE) return@withLock todo.reminderId
        require(todo.state != PhoneResultState.IGNORED)
        val chosen = draft ?: localDraft(row.event, todo.text).copy(deadlineMs = null, estimated = false)
        messages.transaction {
            sms.stageLocal(todo.id, SmsSource(row.event.origin, todo.text), instant(row.event), zone(row.event), chosen)
            val reminderId = sms.confirm(todo.id, chosen, checkDuplicates = false)
            replaceTodo(row, todo.copy(state = if (reminderId == null) PhoneResultState.IGNORED else PhoneResultState.DONE,
                reminderId = reminderId.orEmpty(), reason = ""))
            reminderId
        }
    }

    suspend fun addTodo(id: String, draft: SmsReminderDraft): String? {
        val todoId = mutex.withLock {
            val row = requireNotNull(messages.get(id))
            val todo = PhoneTodo("ph_${row.event.key.take(48)}_manual_${row.todos.size}", "", PhoneResultState.PENDING)
            messages.save(row.copy(todos = row.todos + todo)); todo.id
        }
        return confirmTodo(id, todoId, draft)
    }

    suspend fun ignoreTodo(id: String, todoId: String) = mutex.withLock {
        val row = requireNotNull(messages.get(id))
        val todo = row.todos.single { it.id == todoId }
        require(todo.state != PhoneResultState.DONE)
        messages.transaction { sms.ignore(todoId); replaceTodo(row, todo.copy(state = PhoneResultState.IGNORED)) }
    }

    suspend fun ignoreLedger(id: String) = mutex.withLock {
        val row = requireNotNull(messages.get(id))
        messages.transaction {
            if (row.ledgerId.isNotEmpty()) ledger.dismiss(row.ledgerId, LedgerStatus.IGNORED)
            messages.save(row.copy(ledgerState = PhoneResultState.IGNORED))
        }
    }

    suspend fun retry(id: String): Long? = mutex.withLock {
        val row = requireNotNull(messages.get(id))
        if (!policies.accepts(row.event) || row.textErased) return@withLock null
        val options = policies.optionsFor(row.event)
        val next = row.copy(revision = row.revision + 1, generation = options.generation,
            todos = row.todos.map { if (options.todos && it.state in setOf(PhoneResultState.FAILED, PhoneResultState.QUEUED)) it.copy(state = PhoneResultState.QUEUED, reason = "") else it },
            ledgerState = if (options.ledger && row.ledgerState in setOf(PhoneResultState.FAILED, PhoneResultState.QUEUED)) PhoneResultState.QUEUED else row.ledgerState)
        messages.transaction { messages.save(next); messages.enqueue(next) }
    }

    suspend fun markFailed(id: String) = mutex.withLock {
        val row = messages.get(id) ?: return@withLock
        messages.save(row.copy(revision = row.revision + 1,
            todos = row.todos.map { if (it.state == PhoneResultState.QUEUED) it.copy(state = PhoneResultState.FAILED, reason = "processing_failed") else it },
            ledgerState = if (row.ledgerState == PhoneResultState.QUEUED) PhoneResultState.FAILED else row.ledgerState))
    }

    suspend fun editLedger(id: String, amount: String, currency: String, kind: LedgerKind, merchant: String) =
        mutex.withLock { ledger.edit(id, amount, currency, kind, merchant) }
    suspend fun dismissLedger(id: String, status: LedgerStatus) = mutex.withLock { ledger.dismiss(id, status) }
    suspend fun addLedger(id: String, amount: String, currency: String, kind: LedgerKind, merchant: String) = mutex.withLock {
        val row = requireNotNull(messages.get(id))
        require(row.ledgerId.isEmpty())
        messages.transaction {
            val entry = ledger.addManual(source(row), row.id, row.event.text, row.event.receivedAt, amount, currency, kind, merchant)
            messages.save(row.copy(ledgerId = entry.id, ledgerState = PhoneResultState.DONE)); entry
        }
    }

    suspend fun clearText(id: String) = mutex.withLock {
        val row = requireNotNull(messages.get(id))
        messages.transaction {
            row.todos.forEach { smsSources.clearText(it.id) }
            if (row.ledgerId.isNotEmpty()) ledger.clearText(row.ledgerId)
            messages.save(row.copy(event = row.event.copy(title = "", body = ""), todos = row.todos.map { it.copy(text = "") }, textErased = true))
        }
    }

    suspend fun blockSource(id: String) = mutex.withLock {
        val row = requireNotNull(messages.get(id))
        messages.transaction {
            if (row.event.channel == PhoneChannel.SMS) {
                sms.blockSender(row.event.origin)
                policies.configure(PhoneChannel.SMS, preferences().sms)
            } else policies.selectSource(row.event.origin, false)
        }
    }

    private fun canonicalTodo(text: String): String = text.trim().lowercase()
        .replace(Regex("^(?:请(?:您)?|务必|麻烦|please\\s+)"), "")
        .replace(Regex("取貨|取货|提貨|提货"), "取件")
        .replace("續費", "续费").replace("繳費", "缴费")
        .replace(Regex("\\bpick[ -]?up\\b|\\bcollect\\b"), "pickup")
        .replace(Regex("[\\s，,。；;!?！？]"), "")

    private fun planTodo(event: PhoneEvent, revision: Long, index: Int, text: String): PhoneTodo {
        val dates = SmsDeadlineExtractor.extract(text, instant(event), zone(event))
        val uncertain = dates.size > 1 || dates.singleOrNull()?.let { it.estimated || it.at <= clock.now() } == true ||
            (dates.isEmpty() && Regex("明天|后天|周[一二三四五六日天]|星期|截止|到期|(?i)tomorrow|deadline|before|within").containsMatchIn(text))
        val unclearPayment = Regex("付款|支付|还款|(?i)\\bpay(?:ment)?\\b|repay").containsMatchIn(text) &&
            !Regex("请|需|应|务必|待支付|待付款|待还款|缴费|账单|欠费|截止|到期|(?i)please|must|need|due|before|within").containsMatchIn(text)
        val reason = if (uncertain) "ambiguous_deadline" else if (unclearPayment) "ambiguous_action" else ""
        return PhoneTodo("ph_${event.channel.name}_${event.key.take(42)}_${revision}_$index", text,
            if (reason.isNotEmpty()) PhoneResultState.PENDING else PhoneResultState.QUEUED, reason)
    }

    private fun localDraft(event: PhoneEvent, text: String): SmsReminderDraft {
        val date = SmsDeadlineExtractor.extract(text, instant(event), zone(event)).singleOrNull()?.takeIf { it.at > clock.now() }
        return sms.localDraft(text).copy(deadlineMs = date?.at?.toEpochMilliseconds(), estimated = date?.estimated ?: false)
    }
    private fun active(row: PhoneMessage) = policies.accepts(row.event) && !row.textErased &&
        (row.event.channel != PhoneChannel.SMS || row.event.origin.trim().lowercase() !in smsSources.blockedSenders()) &&
        row.generation == preferences().forChannel(row.event.channel).generation
    private fun sameActive(current: PhoneMessage, snapshot: PhoneMessage) = current.revision == snapshot.revision && active(current)
    private fun replaceTodo(row: PhoneMessage, todo: PhoneTodo) = messages.save(row.copy(todos = row.todos.map { if (it.id == todo.id) todo else it }))
    private fun source(row: PhoneMessage) = if (row.event.channel == PhoneChannel.NOTIFICATION) row.event.origin else "sms:${row.event.origin.take(196)}"
    private fun instant(event: PhoneEvent) = Instant.fromEpochMilliseconds(event.receivedAt)
    private fun zone(event: PhoneEvent) = TimeZone.of(event.zone)
}

package com.dailysatori.bookkeeping

import kotlin.test.*

class BookkeepingTest {
    private val parser = TransactionParser()
    private val engine = LedgerEngine()
    private val payment = "尾号1238消费人民币56.00元，余额1,200.00元。商户：便利店"

    @Test fun selectsTransactionAmountInsteadOfBalance() {
        val draft = assertNotNull(parser.parse(payment))
        assertEquals(5600L, draft.amountMinor)
        assertEquals("1238", draft.accountTail)
        assertEquals("便利店", draft.merchant)
        assertEquals(LedgerKind.EXPENSE, draft.kind)
    }

    @Test fun skipsOtpRemindersFailuresAndChat() {
        listOf("验证码123456，支付金额56元", "请支付人民币56元", "预计明日扣款56元",
            "支付失败56元", "退款申请已提交56元", "今天花了56元真开心", "账户余额56元")
            .forEach { assertNull(parser.parse(it), it) }
    }

    @Test fun incompleteAndAmbiguousTransactionsNeedConfirmation() {
        assertEquals("missing_amount", assertNotNull(parser.parse("支付成功，请查看详情")).reason)
        assertEquals("ambiguous_amount", assertNotNull(parser.parse("支付成功12元，实付10元")).reason)
    }

    @Test fun parsesRefundTransferRepaymentAndGroupedAmounts() {
        assertEquals(LedgerKind.REFUND, assertNotNull(parser.parse("退款成功人民币56.00元")).kind)
        assertEquals(LedgerKind.TRANSFER, assertNotNull(parser.parse("转账成功人民币56.00元")).kind)
        assertEquals(LedgerKind.REPAYMENT, assertNotNull(parser.parse("还款成功人民币56.00元")).kind)
        assertEquals(123456L, assertNotNull(parser.parse("扣款人民币1,234.56元")).amountMinor)
        assertEquals("USD", assertNotNull(parser.parse("消费美元12.50")).currency)
        assertEquals(1200L, assertNotNull(parser.parse("消费日元1200")).amountMinor)
        assertEquals(1200L, assertNotNull(parser.parse("已还款日元1200")).amountMinor)
    }

    @Test fun firstTransactionPostsAndSameEventIsIdempotent() {
        val state = engine.ingest(LedgerState(), "bank", "event1", payment, 1000)
        assertEquals(LedgerStatus.POSTED, state.entries.single().status)
        assertEquals(state, engine.ingest(state, "bank", "event1", payment, 1001))
    }

    @Test fun sameAmountSeparateTransactionsAreWrittenInAndLinkedToTheDuplicate() {
        val state = engine.ingest(LedgerState(), "bank", "event1", payment, 1000)
        val second = engine.ingest(state, "wechat", "event2", "支付成功56元", 2000)
        assertEquals(2, second.entries.size)
        // Suspected duplicates are recorded by default; the user deletes or edits them afterwards.
        assertEquals(LedgerStatus.POSTED, second.entries.last().status)
        assertEquals(second.entries.first().id, second.entries.last().duplicateOf)
        assertEquals(LedgerStatus.POSTED, engine.ingest(state, "bank", "event3", payment, 400000).entries.last().status)
    }

    @Test fun pendingNotificationCanBeEnrichedButPostedEntryIsPreserved() {
        val pending = engine.ingest(LedgerState(), "bank", "event1", "支付成功", 1000)
        val updated = engine.ingest(pending, "bank", "event1", payment, 1001)
        assertEquals(1, updated.entries.size)
        assertEquals(LedgerStatus.POSTED, updated.entries.single().status)
        assertEquals(updated, engine.ingest(updated, "bank", "event1", "支付成功99元", 1002))
    }

    @Test fun explicitTransactionIdDeduplicatesWithinSource() {
        val text = "$payment，交易单号：AB123456"
        val state = engine.ingest(LedgerState(), "bank", "event1", text, 1000)
        val repeated = engine.ingest(state, "bank", "event2", text, 2000)
        assertEquals(1, repeated.entries.size)
        assertEquals(listOf("event1", "event2"), repeated.entries.single().eventKeys)
    }

    @Test fun marketingSummariesAndUnfinishedAuthorizationsAreNotTransactions() {
        listOf("消费满56元享优惠", "本月累计支出56元", "支付申请成功56元", "预授权消费56元",
            "Your card was debited USD56 but the transaction was reversed", "退款处理中56元")
            .forEach { assertNull(parser.parse(it), it) }
    }

    @Test fun deletedTransactionIdStillBlocksRecreationAfterNotificationChanges() {
        val text = "$payment，交易单号：AB123456"
        val state = engine.ingest(LedgerState(), "bank", "event1", text, 1000)
        val deleted = engine.dismiss(state, state.entries.single().id, LedgerStatus.DELETED)
        val repeated = engine.ingest(deleted, "bank", "event2", text, 2000)
        assertEquals(1, repeated.entries.size)
        assertEquals(LedgerStatus.DELETED, repeated.entries.single().status)
    }

    @Test fun pendingTransactionWithSameExplicitIdIsEnrichedAcrossEvents() {
        val state = engine.ingest(LedgerState(), "bank", "event1", "支付成功，交易单号：AB123456", 1000)
        val enriched = engine.ingest(state, "bank", "event2", "支付成功56元，交易单号：AB123456", 2000)
        assertEquals(1, enriched.entries.size)
        assertEquals(LedgerStatus.POSTED, enriched.entries.single().status)
        assertEquals(listOf("event1", "event2"), enriched.entries.single().eventKeys)
    }

    @Test fun totalsSeparateCurrenciesRefundsAndNonExpenseTransactions() {
        var state = LedgerState()
        listOf("消费56元", "到账10元", "退款成功5元", "转账成功20元", "还款成功30元", "消费美元12.50", "支付成功")
            .forEachIndexed { index, text -> state = engine.ingest(state, "bank", "event$index", text, 1000L + index) }
        val totals = engine.totals(state, 1000, 2000)
        assertEquals(LedgerTotal("CNY", 1000, 5600, 500), totals.first { it.currency == "CNY" })
        assertEquals(LedgerTotal("USD", 0, 1250, 0), totals.first { it.currency == "USD" })
        assertTrue(engine.totals(state, 2000, 3000).isEmpty())
    }

    @Test fun editorRejectsInvalidMoneyAndKeepsOtherEntries() {
        val state = engine.ingest(LedgerState(), "bank", "event1", payment, 1000)
        val id = state.entries.single().id
        listOf("0", "-1", "NaN", "12.345", "1,23", "99999999999999999999").forEach {
            assertFailsWith<IllegalArgumentException> { engine.edit(state, id, it, "CNY", LedgerKind.EXPENSE, "") }
        }
        assertFailsWith<IllegalArgumentException> { engine.edit(state, id, "10", "CNY", LedgerKind.UNKNOWN, "") }
        val confirmed = engine.edit(state, id, "58.20", "CNY", LedgerKind.EXPENSE, "修正商户")
        assertEquals(5820L, confirmed.entries.single().amountMinor)
        assertEquals(confirmed, engine.ingest(confirmed, "bank", "event1", payment, 2000))
        assertEquals("58.20", LedgerMoney.format(5820, "CNY"))
        assertEquals("1200", LedgerMoney.format(1200, "JPY"))
    }

    @Test fun paymentAppHeaderDoesNotChangeIncomingTransactionType() {
        assertEquals(LedgerKind.INCOME, assertNotNull(parser.parse("微信支付\n收款到账56元")).kind)
        assertEquals(LedgerKind.INCOME, assertNotNull(parser.parse("支付宝\n收款到账56元")).kind)
    }

    @Test fun repeatedAmountsAcrossLinesRemainAmbiguous() {
        assertEquals("ambiguous_amount", assertNotNull(parser.parse("消费56元\n消费56元")).reason)
    }

    @Test fun recipientAndSenderLabelsDoNotChangeTransactionDirection() {
        assertEquals(LedgerKind.EXPENSE, assertNotNull(parser.parse("支付成功56元，收款方：便利店")).kind)
        assertEquals(LedgerKind.INCOME, assertNotNull(parser.parse("收款到账56元，付款方：客户")).kind)
    }

    @Test fun transactionMetadataDoesNotChangeKindOrCompletion() {
        listOf("支付成功56元，商户：退款服务中心", "消费人民币56元，信用卡还款日为每月20日",
            "支付成功56元 备注：转账费用", "微信支付\n支付成功56元\n商户：退款中心", "支付成功56元，商户名称：退款中心")
            .forEach {
                val draft = assertNotNull(parser.parse(it), it)
                assertEquals(LedgerKind.EXPENSE, draft.kind, it)
                assertEquals("", draft.reason, it)
            }
        assertEquals("unconfirmed", assertNotNull(parser.parse("支付56元，商户：成功便利店")).reason)
    }

    @Test fun unconfirmedReceiptDoesNotPost() {
        listOf("待确认收款56元", "等待确认收款56元", "未到账56元", "未完成收款56元", "待入账56元").forEach {
            assertEquals("unconfirmed", assertNotNull(parser.parse(it)).reason, it)
            assertEquals(LedgerStatus.PENDING, engine.ingest(LedgerState(), "bank", it, it, 1000).entries.single().status)
        }
    }

    @Test fun dollarCurrenciesRequireAnExplicitCountry() {
        mapOf("HK$" to "HKD", "US$" to "USD", "A$" to "AUD", "C$" to "CAD", "S$" to "SGD", "NT$" to "TWD")
            .forEach { (symbol, currency) ->
                val draft = assertNotNull(parser.parse("消费${symbol}56.00"))
                assertEquals(currency, draft.currency, symbol)
                assertEquals(5600L, draft.amountMinor, symbol)
                assertEquals("", draft.reason, symbol)
            }
        val ambiguous = assertNotNull(parser.parse("消费$56.00"))
        assertEquals("ambiguous_currency", ambiguous.reason)
        assertNull(ambiguous.amountMinor)
    }

    @Test fun signedAmountsAreNotSilentlyConvertedToPositive() {
        listOf("消费-56.00元", "消费人民币-56.00", "消费HK$-56.00", "消费−56.00元", "消费+56.00元", "消费-USD56.00").forEach {
            val draft = assertNotNull(parser.parse(it), it)
            assertEquals("invalid_amount", draft.reason, it)
            assertNull(draft.amountMinor, it)
        }
    }

    @Test fun manualCorrectionKeepsOriginalTransactionIdentity() {
        val text = "$payment，交易单号：AB123456"
        val original = engine.ingest(LedgerState(), "bank", "one", text, 1000)
        val corrected = engine.edit(original, original.entries.single().id, "58.20", "USD", LedgerKind.INCOME, "修正")
        val repeated = engine.ingest(corrected, "bank", "two", "消费56元，交易单号：AB123456", 2000)
        assertEquals(corrected.entries.single().copy(eventKeys = listOf("one", "two")), repeated.entries.single())
        assertEquals(repeated, engine.ingest(repeated, "bank", "two", text, 2001))
    }

    @Test fun dismissalStillBlocksRecreationWhenNotificationLosesAccountTail() {
        val text = "$payment，交易单号：AB123456"
        listOf(LedgerStatus.DELETED, LedgerStatus.IGNORED).forEach { status ->
            val original = engine.ingest(LedgerState(), "bank", "one", text, 1000)
            val dismissed = engine.dismiss(original, original.entries.single().id, status)
            val repeated = engine.ingest(dismissed, "bank", "two", "消费99元，交易单号：AB123456", 2000)
            assertEquals(dismissed.entries.single().copy(eventKeys = listOf("one", "two")), repeated.entries.single())
        }
    }

    @Test fun conflictingAmountIsWrittenInAndKeepsTheDuplicateLink() {
        val original = engine.ingest(LedgerState(), "bank", "one", "消费56元，交易单号：AB123456", 1000)
        val conflict = engine.ingest(original, "bank", "two", "消费57元，交易单号：AB123456", 2000)
        assertEquals(2, conflict.entries.size)
        assertEquals(original.entries.single(), conflict.entries.first())
        assertEquals(LedgerStatus.POSTED, conflict.entries.last().status)
        assertEquals(original.entries.single().id, conflict.entries.last().duplicateOf)
    }

    @Test fun differentExplicitAccountTailsDoNotMergeEvenAfterManualConfirmation() {
        val original = engine.ingest(LedgerState(), "bank", "one", "$payment，交易单号：AB123456", 1000)
        val corrected = engine.edit(original, original.entries.single().id, "56", "CNY", LedgerKind.EXPENSE, "")
        val other = engine.ingest(corrected, "bank", "two", "尾号9999消费56元，交易单号：AB123456", 400000)
        assertEquals(2, other.entries.size)
        assertEquals(corrected.entries.single(), other.entries.first())
    }

    @Test fun unknownAccountDoesNotMergeArbitrarilyWhenTransactionIdMatchesTwoAccounts() {
        var state = engine.ingest(LedgerState(), "bank", "one", "尾号1238消费56元，交易单号：AB123456", 1000)
        state = engine.ingest(state, "bank", "two", "尾号9999消费56元，交易单号：AB123456", 400000)
        val unknown = engine.ingest(state, "bank", "three", "消费56元，交易单号：AB123456", 800000)
        assertEquals(3, unknown.entries.size)
        assertEquals(LedgerStatus.POSTED, unknown.entries.last().status)
    }

    @Test fun enrichingNotificationKeepsPreviouslyKnownAccountAndTransactionId() {
        val pending = engine.ingest(LedgerState(), "bank", "one", "尾号1238支付成功，交易单号：AB123456", 1000)
        val enriched = engine.ingest(pending, "bank", "one", "消费56元", 2000)
        assertEquals("1238", enriched.entries.single().accountTail)
        assertEquals("AB123456", enriched.entries.single().transactionId)
        assertEquals(LedgerStatus.POSTED, enriched.entries.single().status)
    }

    @Test fun conflictingTransactionRefreshDoesNotMultiplyEntries() {
        var state = engine.ingest(LedgerState(), "bank", "one", "消费56元，交易单号：AB123456", 1000)
        state = engine.ingest(state, "bank", "two", "消费57元，交易单号：AB123456", 2000)
        val originalAgain = engine.ingest(state, "bank", "three", "消费56元，交易单号：AB123456", 3000)
        assertEquals(2, originalAgain.entries.size)
        assertEquals(listOf("one", "three"), originalAgain.entries.first().eventKeys)
        val conflictAgain = engine.ingest(originalAgain, "bank", "four", "消费57元，交易单号：AB123456", 4000)
        assertEquals(2, conflictAgain.entries.size)
        assertEquals(listOf("two", "four"), conflictAgain.entries.last().eventKeys)
        assertEquals(conflictAgain.entries.first().id, conflictAgain.entries.last().duplicateOf)
    }

    @Test fun mergingAnEnrichedEventRetainsAllEventAliases() {
        var state = engine.ingest(LedgerState(), "bank", "one", "$payment，交易单号：AB123456", 1000)
        state = engine.ingest(state, "bank", "two", "支付成功", 2000)
        val merged = engine.ingest(state, "bank", "two", "消费56元，交易单号：AB123456", 3000)
        assertEquals(1, merged.entries.size)
        assertEquals(listOf("one", "two"), merged.entries.single().eventKeys)
        assertEquals(merged, engine.ingest(merged, "bank", "two", "消费56元", 4000))
    }

    @Test fun retainedTransactionLearnsMissingAccountIdentityBeforeAnotherAccountArrives() {
        var state = engine.ingest(LedgerState(), "bank", "one", "消费56元，交易单号：AB123456", 1000)
        state = engine.ingest(state, "bank", "two", "尾号1238消费56元，交易单号：AB123456", 2000)
        assertEquals("1238", state.entries.single().accountTail)
        val other = engine.ingest(state, "bank", "three", "尾号9999消费56元，交易单号：AB123456", 400000)
        assertEquals(2, other.entries.size)
        assertEquals("9999", other.entries.last().accountTail)
    }
}

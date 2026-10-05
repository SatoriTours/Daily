package com.dailysatori.bookkeeping

class TransactionParser {
    fun parse(text: String): TransactionDraft? {
        if (text.isBlank() || text.length > 8000 || verification.containsMatchIn(text)) return null
        val transactionText = transactionText(text)
        if (excluded.containsMatchIn(transactionText)) return null
        val direct = directAction.containsMatchIn(transactionText)
        val confirmed = completed.containsMatchIn(transactionText)
        if (!direct && !confirmed && !amountLabel.containsMatchIn(transactionText) && !money.containsMatchIn(transactionText)) return null
        if (!action.containsMatchIn(transactionText)) return null
        val amounts = money.findAll(transactionText).filter { isTransactionAmount(transactionText, it.range.first) }.map { match ->
            val prefix = match.groupValues[1].isNotEmpty()
            val currency = currency(match.groupValues[if (prefix) 1 else 4])
            val signedPrefix = transactionText.substring(0, match.range.first).trimEnd().lastOrNull() in signs
            val amount = if (currency == null || signedPrefix) null
                else LedgerMoney.parse(match.groupValues[if (prefix) 2 else 3], currency)
            currency to amount
        }.toList()
        val kind = kind(transactionText)
        val reason = when {
            amounts.isEmpty() -> "missing_amount"
            amounts.size != 1 -> "ambiguous_amount"
            amounts.single().first == null -> "ambiguous_currency"
            amounts.single().second == null -> "invalid_amount"
            kind == LedgerKind.UNKNOWN -> "ambiguous_kind"
            unconfirmed.containsMatchIn(transactionText) || (!direct && !confirmed) -> "unconfirmed"
            else -> ""
        }
        return TransactionDraft(amounts.singleOrNull()?.second, amounts.singleOrNull()?.first ?: "CNY", kind,
            merchant.find(text)?.groupValues?.get(1)?.trim().orEmpty().take(80),
            tail.find(text)?.groupValues?.get(1).orEmpty(),
            transactionId.find(text)?.groupValues?.get(1).orEmpty(), reason)
    }

    private fun transactionText(text: String): String {
        val firstLine = text.substringBefore('\n')
        val body = if (appTitle.matches(firstLine)) text.substringAfter('\n', text) else text
        return body.split(clauseSeparator).joinToString("\n") { clause ->
            metadata.find(clause)?.let { clause.substring(0, it.range.first) } ?: clause
        }
    }

    private fun isTransactionAmount(text: String, start: Int): Boolean {
        val prefix = text.substring(0, start).takeLast(60)
        val lastBalance = balance.findAll(prefix).lastOrNull()?.range?.first ?: -1
        val lastAction = action.findAll(prefix).lastOrNull()?.range?.first ?: -1
        return lastBalance < 0 || lastAction > lastBalance
    }

    private fun kind(text: String): LedgerKind = when {
        refund.containsMatchIn(text) -> LedgerKind.REFUND
        repayment.containsMatchIn(text) -> LedgerKind.REPAYMENT
        transfer.containsMatchIn(text) -> LedgerKind.TRANSFER
        income.containsMatchIn(text) && expense.containsMatchIn(text) -> LedgerKind.UNKNOWN
        income.containsMatchIn(text) -> LedgerKind.INCOME
        expense.containsMatchIn(text) -> LedgerKind.EXPENSE
        else -> LedgerKind.UNKNOWN
    }

    private fun currency(label: String): String? = when (label.uppercase()) {
        "美元", "美金", "US$", "USD" -> "USD"
        "欧元", "€", "EUR" -> "EUR"
        "英镑", "£", "GBP" -> "GBP"
        "港币", "HK$", "HKD" -> "HKD"
        "日元", "JPY" -> "JPY"
        "韩元", "KRW" -> "KRW"
        "新台币", "NT$", "TWD" -> "TWD"
        "新加坡元", "S$", "SGD" -> "SGD"
        "澳元", "A$", "AUD" -> "AUD"
        "加元", "C$", "CAD" -> "CAD"
        "瑞士法郎", "CHF" -> "CHF"
        "$" -> null
        else -> "CNY"
    }

    private companion object {
        val verification = Regex("验证码|驗證碼|动态口令|一次性密码|校验码|verification\\s*code|\\bOTP\\b|one[- ]time\\s*(?:code|password)", RegexOption.IGNORE_CASE)
        val excluded = Regex(listOf(
            "失败|失敗|未成功|取消|撤销|撤銷|待支付|待付款|待扣款|待还款|处理中|處理中|预授权|冻结|申请",
            "请.{0,12}(?:支付|付款|缴费|还款)|预计|将于|即将|应还|最低还款|账单金额|消费满|累计|合计|总计|优惠券|活动|赠送|抽奖",
            "\\b(?:failed|declined|cancelled|canceled|unsuccessful|pending|due|scheduled|reversed)\\b|will\\s+be|refund\\s+requested",
        ).joinToString("|"), RegexOption.IGNORE_CASE)
        val clauseSeparator = Regex("(?<!\\d)[,，]|[,，](?!\\d)|[。；;\\n]")
        val metadata = Regex("(?:(?:商户|商戶|收款方|付款方)(?:名称|名稱)?|备注|備註|附言|用途|merchant|recipient|sender|remark|note)[:：\\s]+|(?:信用卡)?(?:还款|還款)(?:日期|日(?!元))|repayment\\s+date", RegexOption.IGNORE_CASE)
        val unconfirmed = Regex("待确认|待確認|等待|待收款|待领取|待到账|待到賬|待入账|待入賬|未到账|未到賬|未入账|未收款|未支付|未付款|未扣款|未完成|未退款|未还款|尚未|未确认|未確認")
        val refund = Regex("退款|退回|refund", RegexOption.IGNORE_CASE)
        val repayment = Regex("还款|還款|repayment", RegexOption.IGNORE_CASE)
        val transfer = Regex("转账|轉賬|转入|转出|transfer", RegexOption.IGNORE_CASE)
        val income = Regex("收款(?!方|人|账户|账号)|到账|到賬|入账|收入|received|credited", RegexOption.IGNORE_CASE)
        val expense = Regex("消费|消費|支出|支付|付款(?!方|人|账户|账号)|扣款|扣费|paid|payment|purchase|debited", RegexOption.IGNORE_CASE)
        val directAction = Regex("消费|消費|支出|扣款|扣费|收款|到账|到賬|入账|收入|退回|转入|转出|paid|received|credited|debited", RegexOption.IGNORE_CASE)
        val action = Regex("消费|消費|支出|支付|付款|扣款|扣费|收款|到账|到賬|入账|收入|退款|退回|转账|轉賬|转入|转出|还款|paid|payment|purchase|received|credited|debited|refund|transfer|repayment", RegexOption.IGNORE_CASE)
        val completed = Regex("成功|完成|已支付|已付款|已退款|已转账|已还款|successful|completed", RegexOption.IGNORE_CASE)
        val amountLabel = Regex("付款金额|支付金额|交易金额|收款金额")
        val balance = Regex("余额|餘額|可用额度|信用额度|积分|優惠|优惠|折扣|balance", RegexOption.IGNORE_CASE)
        const val LABEL = "人民币|RMB|CNY|美元|USD|美金|欧元|EUR|港币|HKD|日元|JPY|韩元|KRW|英镑|GBP|新台币|TWD|新加坡元|SGD|澳元|AUD|加元|CAD|瑞士法郎|CHF"
        val signs = setOf('+', '-', '−', '－', '＋')
        const val NUMBER = "[+\\-−－＋]?\\s*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?"
        const val SYMBOL = "HK\\$|US\\$|NT\\$|A\\$|C\\$|S\\$|[¥￥$€£]"
        val money = Regex("(?<![\\d.,A-Za-z])(?:(" + LABEL + "|" + SYMBOL + ")\\s*(" + NUMBER + ")|(" + NUMBER + ")\\s*(" + LABEL + "|元))(?![\\d.,])", RegexOption.IGNORE_CASE)
        val merchant = Regex("(?:商户|商戶|收款方|付款方)(?:名称|名稱)?[:：\\s]+([^，。；\\n]+)")
        val tail = Regex("(?:尾号|尾號|尾数)[：:\\s]*([0-9]{4})(?![0-9])")
        val transactionId = Regex("(?:交易单号|交易流水号|流水号|订单号)[：:\\s]*([A-Za-z0-9_-]{6,80})(?![A-Za-z0-9_-])")
        val appTitle = Regex("(?:微信支付|支付宝|支付助手|WeChat Pay|Alipay)(?:[（(].*)?", RegexOption.IGNORE_CASE)
    }
}

package com.dailysatori.service.sms

/** This is the only way to construct SMS text eligible for a remote request. */
object SmsPrivacy {
    private val otp = Regex("验证码|校验码|动态口令|一次性密码|登录码|驗證碼|(?i)\\b(otp|one.time|verification|confirmation code|security code|authentication|passcode|sign.?in|log.?in|2fa|pin)\\b")
    private val identity = Regex("姓名|住址|地址|身份证|身份證|护照|戶名|户名|(?i)\\b(dear|address|passport|ssn|social security|account holder|password|secret)\\b")
    private val links = Regex("(?i)(https?://|www\\.)\\S+|[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}")
    private val account = Regex("(?i)(账号|帳號|账户号码|卡号|卡號|尾号|尾號|账户|賬戶|account|acct|iban|card|a/c)\\s*(?:number|no\\.?|ending|尾号)?\\s*[:：#=]?\\s*[a-zA-Z0-9*•xX][a-zA-Z0-9*•xX ._-]*")
    private val money = Regex("(?i)(?:[$€£¥￥]|(?:NZD|USD|AUD|RMB|CNY)\\s*)[\\s\\d,.]+|[\\d,.]+\\s*(?:元|圆|圓|块|塊|dollars?|NZD|USD|AUD|RMB|CNY)")
    private val chineseMoney = Regex("(?:人民币|人民幣|余额|餘額|金额|金額|欠款|可用额度|可用額度)\\s*[:：]?\\s*[零〇一二三四五六七八九十百千万亿两點点壹贰叁肆伍陆柒捌玖拾佰仟萬億整元圓角分]+")
    private val writtenMoney = Regex("(?i)[零〇一二三四五六七八九十百千万亿两壹贰叁肆伍陆柒捌玖拾佰仟萬億]+(?:元|圆|圓|块|塊|角|分)|\\b(?:zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|twenty|thirty|forty|fifty|hundred|thousand)[a-z -]*\\b(?:dollars?|pounds?|cents?)\\b")
    private val numeric = Regex("[\\p{N}][\\p{N}\\s+.,*/:#_-]*")
    private val balance = Regex("(?i)(余额|餘額|可用额度|可用額度|金额|金額|欠款|balance|amount\\s*(?:due|owing|[:=]))[^,，;；。！？!\\n]*")
    private val action = Regex("(?i)充值|缴费|繳費|续费|續費|取件|取货|取貨|提货|提貨|领取|領取|预约|預約|还款|還款|付款|支付|到期|停机|停機|(?<![a-z])(?:top[ -]?up|pay(?:ment)?|renew|collect|pick[ -]?up|appointment|repay|expire|inactive|due)(?![a-z])")
    private val completed = Regex("(?i)(?:payment|top[ -]?up|transaction|renewal).{0,20}(?:successful|completed|received)|已(?:成功)?(?:充值|缴费|繳費|支付|付款|还款|還款|取件|取货|领取)|支付成功|交易成功")
    private val codeNumber = Regex("(?i)\\b(?:otp|verification|passcode|pin|code)\\s*(?:is\\s*)?[:：=#-]?\\s*\\p{N}{3,8}\\b")
    private val residualMoney = Regex("(?i)[$€£¥￥]|\\b(?:NZD|USD|AUD|RMB|CNY|dollars?|pounds?|cents?)\\b")
    private val writtenNumbers = Regex("(?i)[零〇一二三四五六七八九十百千万亿两壹贰叁肆伍陆柒捌玖拾佰仟萬億]+|\\b(?:zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|thousand|million|billion|trillion|half|quarter|dozen)\\b")

    fun isCandidate(text: String): Boolean = taskClauses(text).isNotEmpty()

    fun taskClauses(text: String): List<String> = text.split(Regex("(?<!\\d)[,，]|[,，](?!\\d)|[。；;\\n]"))
        .map { clause ->
            val metadata = Regex("(?:商户|商戶|收款方|付款方|备注|備註|附言)(?:名称|名稱)?[:：\\s]+|^\\s*(?:信用卡)?还款日(?!元)").find(clause)
            (metadata?.let { clause.substring(0, it.range.first) } ?: clause).trim()
        }.filter { clause ->
            action.containsMatchIn(clause) && !completed.containsMatchIn(clause) &&
                !Regex("处理中|處理中|待确认|待確認|失败|失敗|已取消|退款|优惠券|优惠活动|消费满|抽奖|(?i)\\b(?:processing|pending|failed|cancelled|refund|coupon)\\b").containsMatchIn(clause) &&
                clause !in setOf("微信支付", "支付宝", "支付助手", "WeChat Pay", "Alipay")
        }.distinct().take(10)

    fun isVerification(text: String): Boolean = normalize(text).let { otp.containsMatchIn(it) || codeNumber.containsMatchIn(it) }

    fun aiText(text: String): String? {
        if (text.isBlank() || text.length > 8_000) return null
        val normalized = normalize(text)
        if (isVerification(normalized)) return null
        val withoutLinks = normalized.replace(links, "[REDACTED]")
        if (identity.containsMatchIn(withoutLinks)) return null
        if (withoutLinks.any { it.isLetter() && it !in 'a'..'z' && it !in 'A'..'Z' && it !in '\u4E00'..'\u9FFF' }) return null
        val sanitized = withoutLinks
            .replace(balance, "[BALANCE]").replace(account, "[ACCOUNT]").replace(money, "[AMOUNT]")
            .replace(chineseMoney, "[AMOUNT]").replace(numeric, "[NUMBER]")
            .replace(writtenMoney, "[AMOUNT]")
            .replace(writtenNumbers, "[NUMBER]")
        return sanitized.takeIf { it.none(Char::isDigit) && !residualMoney.containsMatchIn(it) && it.length <= 8_000 }
    }

    private fun normalize(text: String): String = text.filterNot { it in listOf('\u200B', '\u200C', '\u200D', '\uFEFF') }.map { char ->
        when {
            char in '０'..'９' -> '0' + (char - '０')
            char in '\uFF01'..'\uFF5E' -> (char.code - 0xFEE0).toChar()
            else -> char
        }
    }.joinToString("")
}

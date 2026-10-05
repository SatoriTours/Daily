package com.dailysatori.core.bookkeeping

internal fun financialNotificationText(packageName: String, title: String, body: String, groupSummary: Boolean): String? {
    if (groupSummary || body.isBlank() || title.length + body.length + 1 > 8000) return null
    val paymentTitle = when (packageName) {
        "com.tencent.mm" -> Regex("微信支付|微信收款|收款通知|服务通知|WeChat Pay", RegexOption.IGNORE_CASE)
        "com.eg.android.AlipayGphone" -> Regex("支付宝|支付助手|支付成功|付款成功|收款|花呗|借呗|Alipay", RegexOption.IGNORE_CASE)
        else -> null
    }
    if (paymentTitle != null && !paymentTitle.containsMatchIn(title)) return null
    return listOf(title.trim(), body.trim()).filter { it.isNotEmpty() }.joinToString("\n")
}

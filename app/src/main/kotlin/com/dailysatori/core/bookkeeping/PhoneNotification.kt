package com.dailysatori.core.bookkeeping

internal fun phoneNotificationText(packageName: String, title: String, body: String,
    groupSummary: Boolean, ongoing: Boolean, ownPackage: String): String? {
    if (packageName == ownPackage || groupSummary || ongoing || title.length + body.length + 1 > 8000) return null
    if (title.isBlank() && body.isBlank()) return null
    val serviceTitle = when (packageName) {
        "com.tencent.mm" -> Regex("微信支付|微信收款|收款通知|服务通知|WeChat Pay", RegexOption.IGNORE_CASE)
        "com.eg.android.AlipayGphone" -> Regex("支付宝|支付助手|支付成功|付款成功|收款|花呗|借呗|Alipay|服务提醒|预约提醒|快递提醒|生活号", RegexOption.IGNORE_CASE)
        else -> null
    }
    if (serviceTitle != null && !serviceTitle.containsMatchIn(title)) return null
    return listOf(title.trim(), body.trim()).filter(String::isNotEmpty).joinToString("\n")
}

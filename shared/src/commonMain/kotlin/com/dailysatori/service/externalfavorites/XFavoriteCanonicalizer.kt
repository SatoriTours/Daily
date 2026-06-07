package com.dailysatori.service.externalfavorites

private val xStatusUrlPattern = Regex(
    pattern = """^https?://(?:mobile\.)?(?:twitter\.com|x\.com)/([^/?#]+)/status/(\d+)(?:[/?#].*)?$""",
    option = RegexOption.IGNORE_CASE,
)

fun canonicalizeXStatusUrl(url: String): String? {
    val match = xStatusUrlPattern.matchEntire(url.trim()) ?: return null
    val user = match.groupValues[1].trim('@')
    val statusId = match.groupValues[2]
    if (user.isBlank() || statusId.isBlank()) return null
    return "https://x.com/$user/status/$statusId"
}

fun xStatusUrl(statusId: String, username: String?): String {
    val cleanedUsername = username?.trim()?.trim('@').orEmpty()
    return if (cleanedUsername.isBlank()) {
        "https://x.com/i/status/$statusId"
    } else {
        "https://x.com/$cleanedUsername/status/$statusId"
    }
}

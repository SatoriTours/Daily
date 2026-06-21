package com.dailysatori.service.externalfavorites

private val xStatusUrlPattern = Regex(
    pattern = """^https?://(?:mobile\.)?(?:twitter\.com|x\.com)/([^/?#]+)/status/(\d+)(?:[/?#].*)?$""",
    option = RegexOption.IGNORE_CASE,
)
private val xIStatusUrlPattern = Regex(
    pattern = """^https?://(?:mobile\.)?(?:twitter\.com|x\.com)/i/status/(\d+)(?:[/?#].*)?$""",
    option = RegexOption.IGNORE_CASE,
)
private val xArticleUrlPattern = Regex(
    pattern = """^https?://(?:mobile\.)?(?:twitter\.com|x\.com)/i/article/(\d+)(?:[/?#].*)?$""",
    option = RegexOption.IGNORE_CASE,
)
private val xHandlePattern = Regex("""[A-Za-z0-9_]{1,15}""")
private val xReservedRoutes = setOf(
    "compose",
    "explore",
    "hashtag",
    "home",
    "intent",
    "messages",
    "notifications",
    "search",
    "settings",
    "share",
)

fun canonicalizeXStatusUrl(url: String): String? {
    val match = xStatusUrlPattern.matchEntire(url.trim()) ?: return null
    val user = match.groupValues[1].trim('@')
    val statusId = match.groupValues[2]
    if (statusId.isBlank() || !isValidXStatusRoute(user)) return null
    return "https://x.com/$user/status/$statusId"
}

fun xPostIdFromStatusUrl(url: String): String? {
    val value = url.trim()
    xIStatusUrlPattern.matchEntire(value)?.let { return it.groupValues[1] }
    return xStatusUrlPattern.matchEntire(value)
        ?.takeIf { isValidXStatusRoute(it.groupValues[1].trim('@')) }
        ?.groupValues
        ?.getOrNull(2)
}

internal fun isXStatusLikeUrl(url: String): Boolean {
    val value = url.trim()
    if (canonicalizeXStatusUrl(value) != null) return true
    return Regex(
        pattern = """^https?://(?:mobile\.)?(?:twitter\.com|x\.com)/(?:i/status|[^/?#]+/status)/[^/?#]+(?:[/?#].*)?$""",
        option = RegexOption.IGNORE_CASE,
    ).matches(value)
}

internal fun isXArticleUrl(url: String): Boolean =
    xArticleUrlPattern.matches(url.trim())

fun xStatusUrl(statusId: String, username: String?): String {
    val cleanedUsername = username?.trim()?.trim('@').orEmpty()
    return if (cleanedUsername.isBlank()) {
        "https://x.com/i/status/$statusId"
    } else {
        "https://x.com/$cleanedUsername/status/$statusId"
    }
}

private fun isValidXStatusRoute(route: String): Boolean {
    if (route == "i") return true
    return xHandlePattern.matches(route) && route.lowercase() !in xReservedRoutes
}

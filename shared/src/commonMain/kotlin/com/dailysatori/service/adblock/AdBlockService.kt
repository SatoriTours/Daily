package com.dailysatori.service.adblock

import co.touchlab.kermit.Logger

class AdBlockRule(
    val type: String,
    val selector: String? = null,
    val domains: List<String>? = null,
    val pattern: String? = null,
    val options: List<String> = emptyList(),
)

class AdBlockService(rulesText: String) {
    private val log = Logger.withTag("AdBlock")
    private val rules: List<AdBlockRule> = parseRules(rulesText)
    private val cssSelectors: String

    init {
        cssSelectors = buildCssRules()
        log.i { "AdBlock initialized: ${rules.size} rules, ${cssSelectors.length} byte CSS" }
    }

    fun getCssRules(): String = cssSelectors

    fun shouldBlock(url: String): Boolean {
        val lowerUrl = url.lowercase()
        for (rule in rules) {
            if (rule.type == "block") {
                val pattern = rule.pattern ?: continue
                if (matchUrlPattern(lowerUrl, pattern)) {
                    if (rule.domains != null && rule.domains.isNotEmpty()) {
                        val blocked = rule.domains.any { domain ->
                            rule.options.any { opt -> opt == "~third-party" } ||
                                lowerUrl.contains(domain.replace("||", ""))
                        }
                        if (!blocked) continue
                    }
                    return true
                }
            }
        }
        return false
    }

    private fun buildCssRules(): String {
        val selectors = mutableListOf<String>()
        for (rule in rules) {
            if (rule.type == "cosmetic" && rule.selector != null) {
                if (rule.domains.isNullOrEmpty()) {
                    selectors.add(rule.selector)
                }
            }
        }
        return if (selectors.isNotEmpty()) {
            selectors.joinToString(", ") + " { display: none !important; }"
        } else ""
    }

    private fun parseRules(text: String): List<AdBlockRule> {
        val result = mutableListOf<AdBlockRule>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("[")) continue
            try {
                val rule = parseLine(trimmed)
                if (rule != null) result.add(rule)
            } catch (_: Exception) {}
        }
        return result
    }

    private fun parseLine(line: String): AdBlockRule? {
        // Cosmetic hiding rules: ##.selector, domain.com##.selector, #@#.selector (exception)
        if (line.contains("##") || line.contains("#@#")) {
            val isException = line.contains("#@#")
            val separator = if (isException) "#@#" else "##"
            val parts = line.split(separator, limit = 2)
            val domains = if (parts[0].isNotEmpty()) parts[0].split(",").map { it.trim() } else null
            val selector = parts.getOrNull(1)?.trim() ?: return null
            if (selector.isEmpty()) return null
            return AdBlockRule(
                type = if (isException) "cosmetic-exception" else "cosmetic",
                selector = if (domains != null) domains.joinToString(",") + " " + selector else selector,
                domains = domains,
            )
        }

        // Network block rules: ||domain.com^, ||domain.com/path, |http://...
        if (line.startsWith("||") || line.startsWith("|http")) {
            var pattern = line
            val options = mutableListOf<String>()
            val dollarIndex = pattern.lastIndexOf('$')
            if (dollarIndex > 0) {
                options.addAll(pattern.substring(dollarIndex + 1).split(",").map { it.trim() })
                pattern = pattern.substring(0, dollarIndex)
            }
            pattern = pattern
                .replace("||", "")
                .replace("^", "")
                .replace("*", ".*")
            if (pattern.endsWith("|")) pattern = pattern.dropLast(1)
            return AdBlockRule(
                type = "block",
                pattern = pattern,
                options = options,
            )
        }

        // Domain-only rules
        if (line.startsWith(".") || line.startsWith("/")) {
            return AdBlockRule(
                type = "block",
                pattern = line.replace("^", ""),
            )
        }

        return null
    }

    private fun matchUrlPattern(url: String, pattern: String): Boolean {
        val lowerPattern = pattern.lowercase()
        if (lowerPattern.startsWith(".")) {
            return url.contains(lowerPattern)
        }
        return url.contains(lowerPattern)
    }
}

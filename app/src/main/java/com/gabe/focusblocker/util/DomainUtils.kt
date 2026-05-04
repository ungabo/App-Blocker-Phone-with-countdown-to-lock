package com.gabe.focusblocker.util

import java.util.Locale

object DomainUtils {
    private val domainCandidateRegex =
        Regex("""(?i)\b(?:https?://)?(?:[a-z0-9-]+\.)+[a-z]{2,63}(?::\d{1,5})?(?:/[^\s]*)?""")

    fun normalizeDomain(domain: String): String {
        return domain
            .trim()
            .lowercase(Locale.US)
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore(":")
            .removePrefix("www.")
    }

    fun matchesDomain(domain: String, ruleDomain: String): Boolean {
        val normalizedDomain = normalizeDomain(domain)
        val normalizedRule = normalizeDomain(ruleDomain)
        return normalizedDomain == normalizedRule || normalizedDomain.endsWith(".$normalizedRule")
    }

    fun matchesAny(domain: String, rules: Set<String>): Boolean {
        val normalized = normalizeDomain(domain)
        return rules.any { matchesDomain(normalized, it) }
    }

    fun extractDomainCandidates(text: String): Set<String> {
        return domainCandidateRegex
            .findAll(text)
            .map { normalizeDomain(it.value) }
            .filter { it.contains(".") && it.length <= 253 }
            .toSet()
    }
}

package com.nandox.arcade.browser

import java.net.URLEncoder

object UrlResolver {
    private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    fun resolve(input: String): String {
        val text = input.trim()
        if (text.isEmpty()) return search("")
        val lower = text.lowercase()
        if (lower.startsWith("https://") || lower.startsWith("http://")) return text
        if (scheme.containsMatchIn(text)) return search(text)
        if (' ' !in text && '.' in text) return "https://$text"
        return search(text)
    }

    private fun search(query: String) =
        "https://duckduckgo.com/?q=" + URLEncoder.encode(query, "UTF-8")

    fun isAllowedNavigation(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("https://") || lower.startsWith("http://")
    }
}

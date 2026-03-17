package app.justtalk.core.config

object UrlValidators {
    fun isValidSignalingUrl(url: String): Boolean {
        val u = url.trim()
        if (!(u.startsWith("ws://") || u.startsWith("wss://"))) return false
        if (u.contains("not-configured.invalid")) return false
        return true
    }

    /**
     * Accepts ws/wss URLs as-is, and converts http/https to ws/wss.
     * Returns null if the resulting URL is not suitable for signaling.
     */
    fun normalizeSignalingUrl(input: String?): String? {
        val raw0 = input?.trim().orEmpty()
        if (raw0.isBlank()) return null
        // Trim trailing slashes/spaces that often appear when copying.
        val raw = raw0.trim().trimEnd('/')
        if (raw.isBlank()) return null

        val wsUrl =
            when {
                raw.startsWith("ws://") || raw.startsWith("wss://") -> raw
                raw.startsWith("https://") -> "wss://" + raw.removePrefix("https://")
                raw.startsWith("http://") -> "ws://" + raw.removePrefix("http://")
                // Accept bare hosts like "projects-...trycloudflare.com"
                raw.contains("://").not() -> "wss://$raw"
                else -> raw
            }

        return if (isValidSignalingUrl(wsUrl)) wsUrl else null
    }
}


package app.justtalk.core.config

object UrlValidators {
    fun isValidSignalingUrl(url: String): Boolean {
        val u = url.trim()
        if (!(u.startsWith("ws://") || u.startsWith("wss://"))) return false
        if (u.contains("not-configured.invalid")) return false
        return true
    }
}


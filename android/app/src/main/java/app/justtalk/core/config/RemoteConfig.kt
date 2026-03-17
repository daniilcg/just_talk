package app.justtalk.core.config

import app.justtalk.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RemoteConfig(
    val signalingUrl: String?
) {
    companion object {
        fun fetch(http: OkHttpClient = OkHttpClient()): RemoteConfig? = fetchDebug(http).first

        /**
         * Returns Pair(config, error).
         * Error is a short string that can be shown to the user for troubleshooting.
         */
        fun fetchDebug(http: OkHttpClient = OkHttpClient()): Pair<RemoteConfig?, String?> {
            val client =
                http.newBuilder()
                    .connectTimeout(6, TimeUnit.SECONDS)
                    .readTimeout(6, TimeUnit.SECONDS)
                    .callTimeout(8, TimeUnit.SECONDS)
                    .build()

            // Fallback mirrors: if GitHub Raw is blocked/slow, use CDN.
            val urls = listOf(
                BuildConfig.REMOTE_CONFIG_URL,
                "https://cdn.jsdelivr.net/gh/daniilcg/just_talk@main/config/justtalk.json"
            )

            var lastErr: String? = null
            for (u in urls) {
                val res = tryFetchOnce(client, u)
                if (res.first != null) return res
                lastErr = res.second
            }
            return null to (lastErr ?: "no_config")
        }

        private fun tryFetchOnce(http: OkHttpClient, url: String): Pair<RemoteConfig?, String?> {
            return try {
                val req = Request.Builder().url(url).build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return null to "http_${resp.code} (${hostOf(url)})"
                    }
                    val body = resp.body?.string().orEmpty()
                    val obj = JSONObject(body)
                    val signaling = obj.optString("signalingUrl").trim().takeIf { it.isNotBlank() }
                    RemoteConfig(signalingUrl = signaling) to null
                }
            } catch (e: Exception) {
                null to "${e.javaClass.simpleName}${e.message?.let { ": $it" }.orEmpty()} (${hostOf(url)})"
            }
        }

        private fun hostOf(url: String): String {
            val noProto = url.substringAfter("://", url)
            return noProto.substringBefore("/", noProto)
        }
    }
}


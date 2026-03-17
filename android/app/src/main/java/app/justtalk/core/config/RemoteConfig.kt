package app.justtalk.core.config

import android.util.Base64
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
                "https://cdn.jsdelivr.net/gh/daniilcg/just_talk@main/config/justtalk.json",
                // GitHub API fallback (often works when Raw is blocked)
                "https://api.github.com/repos/daniilcg/just_talk/contents/config/justtalk.json?ref=main"
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
                val req =
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", "JustTalk/1.0")
                        .build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return null to "http_${resp.code} (${hostOf(url)})"
                    }
                    val body = resp.body?.string().orEmpty()
                    val payload =
                        if (hostOf(url) == "api.github.com") {
                            // { content: "<base64>", encoding: "base64" }
                            val apiObj = JSONObject(body)
                            val enc = apiObj.optString("encoding")
                            val content = apiObj.optString("content")
                            if (enc != "base64" || content.isBlank()) return null to "bad_api_payload (api.github.com)"
                            val decoded = Base64.decode(content.replace("\n", ""), Base64.DEFAULT)
                            String(decoded)
                        } else {
                            body
                        }
                    val obj = JSONObject(payload)
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


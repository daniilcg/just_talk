package app.justtalk.core.config

import app.justtalk.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

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
            return try {
                val req = Request.Builder()
                    .url(BuildConfig.REMOTE_CONFIG_URL)
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return null to "http_${resp.code}"
                    }
                    val body = resp.body?.string().orEmpty()
                    val obj = JSONObject(body)
                    val signaling = obj.optString("signalingUrl").trim().takeIf { it.isNotBlank() }
                    RemoteConfig(signalingUrl = signaling) to null
                }
            } catch (e: Exception) {
                null to "${e.javaClass.simpleName}${e.message?.let { ": $it" }.orEmpty()}"
            }
        }
    }
}


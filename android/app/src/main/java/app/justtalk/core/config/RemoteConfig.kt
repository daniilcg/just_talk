package app.justtalk.core.config

import app.justtalk.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class RemoteConfig(
    val signalingUrl: String?
) {
    companion object {
        fun fetch(http: OkHttpClient = OkHttpClient()): RemoteConfig? {
            return runCatching {
                val req = Request.Builder()
                    .url(BuildConfig.REMOTE_CONFIG_URL)
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    val body = resp.body?.string().orEmpty()
                    val obj = JSONObject(body)
                    val signaling = obj.optString("signalingUrl").takeIf { it.isNotBlank() }
                    RemoteConfig(signalingUrl = signaling)
                }
            }.getOrNull()
        }
    }
}


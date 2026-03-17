package app.justtalk.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ChatLine(
    val dir: String, // "out" | "in"
    val text: String,
    val tsMs: Long
)

class ChatHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chat_history", Context.MODE_PRIVATE)

    fun load(withUid: String): List<ChatLine> {
        val key = key(withUid)
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ChatLine(
                            dir = o.optString("dir"),
                            text = o.optString("text"),
                            tsMs = o.optLong("tsMs", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun append(withUid: String, line: ChatLine, maxItems: Int = 200) {
        val key = key(withUid)
        val cur = load(withUid).toMutableList()
        cur.add(line)
        val trimmed = if (cur.size > maxItems) cur.takeLast(maxItems) else cur
        val arr = JSONArray()
        for (l in trimmed) {
            arr.put(
                JSONObject()
                    .put("dir", l.dir)
                    .put("text", l.text)
                    .put("tsMs", l.tsMs)
            )
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun key(withUid: String) = "u:" + withUid.trim().lowercase()
}


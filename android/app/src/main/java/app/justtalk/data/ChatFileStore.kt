package app.justtalk.data

import android.content.Context
import java.io.File

/**
 * ICQ/QIP-like local history: one file per contact, stored on device only.
 *
 * Format: TSV per line: tsMs \t dir(in|out) \t text
 */
class ChatFileStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "chats").apply { mkdirs() }

    fun fileFor(withUid: String): File = File(dir, "${sanitize(withUid)}.txt")

    fun load(withUid: String, maxLines: Int = 500): List<ChatLine> {
        val f = fileFor(withUid)
        if (!f.exists()) return emptyList()
        return runCatching {
            f.readLines()
                .takeLast(maxLines)
                .mapNotNull { line ->
                    val parts = line.split('\t', limit = 3)
                    if (parts.size < 3) return@mapNotNull null
                    val ts = parts[0].toLongOrNull() ?: 0L
                    val dir = parts[1]
                    val text = parts[2]
                    ChatLine(dir = dir, text = text, tsMs = ts)
                }
        }.getOrDefault(emptyList())
    }

    fun append(withUid: String, line: ChatLine) {
        val f = fileFor(withUid)
        val safeText = line.text.replace("\n", " ").replace("\r", " ")
        f.appendText("${line.tsMs}\t${line.dir}\t$safeText\n")
    }

    private fun sanitize(uid: String): String =
        uid.trim().lowercase().map { ch ->
            if (ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == '.') ch else '_'
        }.joinToString("").take(64)
}


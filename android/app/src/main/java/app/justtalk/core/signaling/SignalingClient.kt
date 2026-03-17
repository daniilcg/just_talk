package app.justtalk.core.signaling

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import app.justtalk.core.logging.AppLog

sealed interface SignalingEvent {
    data class Joined(val room: String, val peerId: String, val peers: List<String>) : SignalingEvent
    data class PeerJoined(val peerId: String) : SignalingEvent
    data class PeerLeft(val peerId: String) : SignalingEvent
    data class Signal(val from: String, val to: String?, val payload: JSONObject) : SignalingEvent
    data class Error(val code: String, val details: String? = null) : SignalingEvent
    data object Closed : SignalingEvent
}

class SignalingClient(
    private val url: String,
    private val roomId: String,
    private val peerId: String
) {
    private val okHttp = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var ws: WebSocket? = null

    private val _events = MutableSharedFlow<SignalingEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<SignalingEvent> = _events

    fun connect() {
        AppLog.i("SignalingClient", "connect url=${url.take(200)} room=$roomId peer=${peerId.take(8)}…")
        val request = Request.Builder().url(url).build()
        ws = okHttp.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val join = JSONObject()
                    .put("type", "join")
                    .put("room", roomId)
                    .put("peerId", peerId)
                webSocket.send(join.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = runCatching { JSONObject(text) }.getOrNull()
                if (obj == null) {
                    _events.tryEmit(SignalingEvent.Error("bad_json"))
                    return
                }
                when (obj.optString("type")) {
                    "joined" -> {
                        val peersJson = obj.optJSONArray("peers")
                        val peers = buildList {
                            if (peersJson != null) {
                                for (i in 0 until peersJson.length()) add(peersJson.optString(i))
                            }
                        }
                        _events.tryEmit(
                            SignalingEvent.Joined(
                                room = obj.optString("room"),
                                peerId = obj.optString("peerId"),
                                peers = peers
                            )
                        )
                        AppLog.i("SignalingClient", "joined room=$roomId peers=${peers.size}")
                    }
                    "peer_joined" -> {
                        val p = obj.optString("peerId")
                        _events.tryEmit(SignalingEvent.PeerJoined(p))
                        AppLog.i("SignalingClient", "peer_joined peer=${p.take(8)}…")
                    }
                    "peer_left" -> {
                        val p = obj.optString("peerId")
                        _events.tryEmit(SignalingEvent.PeerLeft(p))
                        AppLog.i("SignalingClient", "peer_left peer=${p.take(8)}…")
                    }
                    "signal" -> {
                        val payload = obj.optJSONObject("payload") ?: JSONObject()
                        val toVal = if (obj.isNull("to")) null else obj.optString("to")
                        _events.tryEmit(
                            SignalingEvent.Signal(
                                from = obj.optString("from"),
                                to = toVal,
                                payload = payload
                            )
                        )
                        AppLog.d("SignalingClient", "signal from=${obj.optString("from").take(8)}… to=${toVal?.take(8)} kind=${payload.optString("kind")}")
                    }
                    "error" -> _events.tryEmit(
                        SignalingEvent.Error(
                            code = obj.optString("code", "error"),
                            details = obj.optString("details", null)
                        )
                    )
                    else -> Unit
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                AppLog.w("SignalingClient", "closed code=$code reason=$reason")
                _events.tryEmit(SignalingEvent.Closed)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                AppLog.w("SignalingClient", "ws_failure ${t.message}", t)
                _events.tryEmit(SignalingEvent.Error("ws_failure", t.message))
            }
        })
    }

    fun sendSignal(to: String?, payload: JSONObject) {
        val obj = JSONObject()
            .put("type", "signal")
            .put("room", roomId)
            .put("from", peerId)
            .put("to", to)
            .put("payload", payload)
        AppLog.d("SignalingClient", "send signal to=${to?.take(8)} kind=${payload.optString("kind")}")
        ws?.send(obj.toString())
    }

    fun close() {
        val obj = JSONObject()
            .put("type", "leave")
            .put("room", roomId)
            .put("peerId", peerId)
        ws?.send(obj.toString())
        ws?.close(1000, "bye")
        ws = null
    }
}


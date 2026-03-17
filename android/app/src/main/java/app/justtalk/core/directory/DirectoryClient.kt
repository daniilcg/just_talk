package app.justtalk.core.directory

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

sealed interface DirectoryEvent {
    data class SignupOk(val uid: String, val nickname: String, val email: String?, val displayName: String?, val bio: String?, val status: String?) : DirectoryEvent
    data class LoginOk(val uid: String, val nickname: String, val email: String?, val displayName: String?, val bio: String?, val status: String?) : DirectoryEvent
    data class LookupUidResult(val uid: String, val nickname: String?, val onlinePeerId: String?, val displayName: String?, val bio: String?, val status: String?) : DirectoryEvent
    data class LookupNicknameResult(val nickname: String, val uid: String?, val onlinePeerId: String?, val displayName: String?, val bio: String?, val status: String?) : DirectoryEvent
    data class Invite(val fromPeerId: String, val roomId: String) : DirectoryEvent
    data class InviteResult(val ok: Boolean, val reason: String? = null) : DirectoryEvent
    data class Msg(val fromUid: String, val text: String, val tsMs: Long) : DirectoryEvent
    data class MsgResult(val ok: Boolean, val reason: String? = null) : DirectoryEvent
    data class SetProfileOk(val ok: Boolean) : DirectoryEvent
    data class SetStatusOk(val ok: Boolean) : DirectoryEvent
    data object SetFcmTokenOk : DirectoryEvent
    data class Error(val code: String, val details: String? = null) : DirectoryEvent
    data object Closed : DirectoryEvent
}

class DirectoryClient(
    private val url: String
) {
    private val okHttp = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var ws: WebSocket? = null

    private val _events = MutableSharedFlow<DirectoryEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<DirectoryEvent> = _events

    fun connect() {
        AppLog.i("DirectoryClient", "connect url=${url.take(200)}")
        val request = Request.Builder().url(url).build()
        ws = okHttp.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = Unit

            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = runCatching { JSONObject(text) }.getOrNull()
                if (obj == null) {
                    _events.tryEmit(DirectoryEvent.Error("bad_json"))
                    return
                }
                when (obj.optString("type")) {
                    "signup_ok" -> _events.tryEmit(
                        DirectoryEvent.SignupOk(
                            uid = obj.optString("uid"),
                            nickname = obj.optString("nickname"),
                            email = if (obj.isNull("email")) null else obj.optString("email"),
                            displayName = if (obj.isNull("displayName")) null else obj.optString("displayName"),
                            bio = if (obj.isNull("bio")) null else obj.optString("bio"),
                            status = if (obj.isNull("status")) null else obj.optString("status")
                        )
                    )
                    "login_ok" -> _events.tryEmit(
                        DirectoryEvent.LoginOk(
                            uid = obj.optString("uid"),
                            nickname = obj.optString("nickname"),
                            email = if (obj.isNull("email")) null else obj.optString("email"),
                            displayName = if (obj.isNull("displayName")) null else obj.optString("displayName"),
                            bio = if (obj.isNull("bio")) null else obj.optString("bio"),
                            status = if (obj.isNull("status")) null else obj.optString("status")
                        )
                    )
                    "lookup_uid_result" -> _events.tryEmit(
                        DirectoryEvent.LookupUidResult(
                            uid = obj.optString("uid"),
                            nickname = if (obj.isNull("nickname")) null else obj.optString("nickname"),
                            onlinePeerId = if (obj.isNull("onlinePeerId")) null else obj.optString("onlinePeerId"),
                            displayName = if (obj.isNull("displayName")) null else obj.optString("displayName"),
                            bio = if (obj.isNull("bio")) null else obj.optString("bio"),
                            status = if (obj.isNull("status")) null else obj.optString("status")
                        )
                    )
                    "lookup_nickname_result" -> _events.tryEmit(
                        DirectoryEvent.LookupNicknameResult(
                            nickname = obj.optString("nickname"),
                            uid = if (obj.isNull("uid")) null else obj.optString("uid"),
                            onlinePeerId = if (obj.isNull("onlinePeerId")) null else obj.optString("onlinePeerId"),
                            displayName = if (obj.isNull("displayName")) null else obj.optString("displayName"),
                            bio = if (obj.isNull("bio")) null else obj.optString("bio"),
                            status = if (obj.isNull("status")) null else obj.optString("status")
                        )
                    )
                    "invite" -> _events.tryEmit(
                        DirectoryEvent.Invite(
                            fromPeerId = obj.optString("from"),
                            roomId = obj.optString("room")
                        )
                    )
                    "invite_result" -> _events.tryEmit(
                        DirectoryEvent.InviteResult(
                            ok = obj.optBoolean("ok", false),
                            reason = obj.optString("reason", null)
                        )
                    )
                    "msg" -> _events.tryEmit(
                        DirectoryEvent.Msg(
                            fromUid = obj.optString("fromUid"),
                            text = obj.optString("text"),
                            tsMs = obj.optLong("tsMs", 0L)
                        )
                    )
                    "msg_result" -> _events.tryEmit(
                        DirectoryEvent.MsgResult(
                            ok = obj.optBoolean("ok", false),
                            reason = obj.optString("reason", null)
                        )
                    )
                    "set_profile_ok" -> _events.tryEmit(
                        DirectoryEvent.SetProfileOk(ok = obj.optBoolean("ok", false))
                    )
                    "set_status_ok" -> _events.tryEmit(
                        DirectoryEvent.SetStatusOk(ok = obj.optBoolean("ok", false))
                    )
                    "set_fcm_token_ok" -> _events.tryEmit(DirectoryEvent.SetFcmTokenOk)
                    "error" -> _events.tryEmit(
                        DirectoryEvent.Error(
                            code = obj.optString("code", "error"),
                            details = obj.optString("details", null)
                        )
                    )
                    else -> Unit
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                AppLog.w("DirectoryClient", "closed code=$code reason=$reason")
                _events.tryEmit(DirectoryEvent.Closed)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                AppLog.w("DirectoryClient", "ws_failure ${t.message}", t)
                _events.tryEmit(DirectoryEvent.Error("ws_failure", t.message))
            }
        })
    }

    fun signup(nickname: String, email: String?, peerId: String, password: String) {
        val obj = JSONObject()
            .put("type", "signup")
            .put("nickname", nickname.trim())
            .put("email", email?.trim() ?: JSONObject.NULL)
            .put("password", password)
            .put("peerId", peerId)
        ws?.send(obj.toString())
    }

    fun login(uid: String, password: String, peerId: String) {
        val obj = JSONObject()
            .put("type", "login")
            .put("uid", uid.trim())
            .put("password", password)
            .put("peerId", peerId)
        ws?.send(obj.toString())
    }

    fun lookupUid(uid: String) {
        val obj = JSONObject()
            .put("type", "lookup_uid")
            .put("uid", uid.trim())
        ws?.send(obj.toString())
    }

    fun lookupNickname(nickname: String) {
        val obj = JSONObject()
            .put("type", "lookup_nickname")
            .put("nickname", nickname.trim())
        ws?.send(obj.toString())
    }

    fun inviteUid(fromPeerId: String, toUid: String, roomId: String) {
        val obj = JSONObject()
            .put("type", "invite_uid")
            .put("from", fromPeerId)
            .put("toUid", toUid.trim())
            .put("room", roomId)
        AppLog.i("DirectoryClient", "send invite_uid toUid=${toUid.trim()} room=$roomId")
        ws?.send(obj.toString())
    }

    fun sendMsgUid(toUid: String, text: String) {
        val obj = JSONObject()
            .put("type", "msg_uid")
            .put("toUid", toUid.trim())
            .put("text", text)
        AppLog.i("DirectoryClient", "send msg_uid toUid=${toUid.trim()} len=${text.length}")
        ws?.send(obj.toString())
    }

    fun setFcmToken(token: String) {
        val obj = JSONObject()
            .put("type", "set_fcm_token")
            .put("token", token)
        ws?.send(obj.toString())
    }

    fun setProfile(displayName: String, bio: String) {
        val obj = JSONObject()
            .put("type", "set_profile")
            .put("displayName", displayName)
            .put("bio", bio)
        ws?.send(obj.toString())
    }

    fun setStatus(status: String) {
        val obj = JSONObject()
            .put("type", "set_status")
            .put("status", status)
        ws?.send(obj.toString())
    }

    fun close() {
        ws?.close(1000, "bye")
        ws = null
    }
}


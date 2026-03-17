package app.justtalk.core.directory

import android.content.Context
import app.justtalk.data.ProfileStore
import app.justtalk.data.SecurePasswordStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Single app-level Directory connection.
 * Keeps WebSocket + login alive across screens to avoid online/offline flapping.
 */
class DirectorySession(
    private val appContext: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = ProfileStore(appContext)
    private val secure = SecurePasswordStore(appContext)

    private var client: DirectoryClient? = null

    private val _events = MutableSharedFlow<DirectoryEvent>(
        replay = 0,
        extraBufferCapacity = 256
    )
    val events: SharedFlow<DirectoryEvent> = _events

    @Volatile
    var status: String = "Отключено"
        private set

    fun start() {
        scope.launch {
            // Reconnect when signalingUrl changes.
            store.signalingUrl.collectLatest { url ->
                connectLoop(url)
            }
        }
    }

    private suspend fun connectLoop(url: String) {
        close()
        val normalized = app.justtalk.core.config.UrlValidators.normalizeSignalingUrl(url)
        if (normalized == null) {
            status = "Сервер не настроен"
            return
        }
        while (true) {
            val c = DirectoryClient(normalized)
            client = c
            status = "Подключение…"
            c.connect()

            val uid = store.uid.first().orEmpty()
            val peerId = store.ensurePeerId()
            val pass = secure.getPassword().orEmpty()
            if (uid.isNotBlank() && pass.length >= 6) {
                c.login(uid = uid, password = pass, peerId = peerId)
            }

            try {
                c.events.collectLatest { ev ->
                    when (ev) {
                        is DirectoryEvent.LoginOk -> status = "Онлайн"
                        is DirectoryEvent.Error -> status = "Ошибка: ${ev.code}"
                        DirectoryEvent.Closed -> status = "Отключено"
                        else -> Unit
                    }
                    _events.tryEmit(ev)
                    if (ev == DirectoryEvent.Closed) throw RuntimeException("closed")
                }
            } catch (e: Exception) {
                close()
                status = "Переподключение…"
                delay(800)
                continue
            }
        }
    }

    fun lookupUid(uid: String) {
        client?.lookupUid(uid)
    }

    fun inviteUid(toUid: String, roomId: String) {
        scope.launch {
            val fromPeer = store.ensurePeerId()
            client?.inviteUid(fromPeerId = fromPeer, toUid = toUid, roomId = roomId)
        }
    }

    fun sendMsg(toUid: String, text: String) {
        client?.sendMsgUid(toUid = toUid, text = text)
    }

    fun setProfile(displayName: String, bio: String) {
        client?.setProfile(displayName = displayName, bio = bio)
    }

    fun setStatus(status: String) {
        client?.setStatus(status)
    }

    fun close() {
        client?.close()
        client = null
    }
}


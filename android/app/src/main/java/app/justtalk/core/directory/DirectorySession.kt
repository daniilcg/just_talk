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
        if (url.isBlank()) {
            status = "Сервер не настроен"
            return
        }
        while (true) {
            val c = DirectoryClient(url)
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
            } catch {
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
        val fromPeer = runCatching { store.ensurePeerId() }.getOrNull()
        if (fromPeer != null) client?.inviteUid(fromPeerId = fromPeer, toUid = toUid, roomId = roomId)
    }

    fun sendMsg(toUid: String, text: String) {
        client?.sendMsgUid(toUid = toUid, text = text)
    }

    fun close() {
        client?.close()
        client = null
    }
}


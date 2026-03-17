package app.justtalk.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.justtalk.core.chat.ChatMessage
import app.justtalk.core.chat.DataChannelChat
import app.justtalk.core.signaling.SignalingClient
import app.justtalk.core.signaling.SignalingEvent
import app.justtalk.core.webrtc.WebRtcClient
import app.justtalk.data.ProfileStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallScreen(
    roomId: String,
    onHangup: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { ProfileStore(context) }
    val scope = rememberCoroutineScope()

    var hasPerms by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Подготовка…") }
    var micOn by remember { mutableStateOf(true) }
    var camOn by remember { mutableStateOf(true) }

    val permsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPerms = (result[Manifest.permission.RECORD_AUDIO] == true) && (result[Manifest.permission.CAMERA] == true)
    }

    LaunchedEffect(Unit) {
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasPerms = mic && cam
        if (!hasPerms) permsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
    }

    if (!hasPerms) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Нужны разрешения на микрофон и камеру.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = { permsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)) }) {
                Text("Разрешить")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onHangup) { Text("Назад") }
        }
        return
    }

    val eglBase = remember { EglBase.create() }

    var localRenderer: SurfaceViewRenderer? by remember { mutableStateOf(null) }
    var remoteRenderer: SurfaceViewRenderer? by remember { mutableStateOf(null) }
    var dataChannel: DataChannel? by remember { mutableStateOf(null) }
    var chat: DataChannelChat? by remember { mutableStateOf(null) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var messageText by remember { mutableStateOf("") }

    var webrtc: WebRtcClient? by remember { mutableStateOf(null) }
    var signaling: SignalingClient? by remember { mutableStateOf(null) }
    var peerId by remember { mutableStateOf("") }
    var targetPeerId: String? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        onDispose {
            chat?.stop()
            signaling?.close()
            webrtc?.close()
            runCatching { localRenderer?.release() }
            runCatching { remoteRenderer?.release() }
            runCatching { eglBase.release() }
        }
    }

    LaunchedEffect(roomId) {
        peerId = store.ensurePeerId()
        val url = store.signalingUrl.first()
        status = "Подключение к сигналингу…"
        signaling = SignalingClient(url = url, roomId = roomId, peerId = peerId).also { it.connect() }
    }

    LaunchedEffect(signaling) {
        val s = signaling ?: return@LaunchedEffect
        s.events.collectLatest { ev ->
            when (ev) {
                is SignalingEvent.Joined -> {
                    status = "Комната: $roomId"
                    targetPeerId = ev.peers.firstOrNull()
                    if (webrtc == null) {
                        val turnUrl = store.turnUrl.first().orEmpty()
                        val turnUser = store.turnUser.first().orEmpty()
                        val turnPass = store.turnPass.first().orEmpty()
                        val ice = buildList {
                            add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
                            if (turnUrl.isNotBlank() && turnUser.isNotBlank() && turnPass.isNotBlank()) {
                                add(
                                    PeerConnection.IceServer.builder(turnUrl)
                                        .setUsername(turnUser)
                                        .setPassword(turnPass)
                                        .createIceServer()
                                )
                            }
                        }
                        webrtc = WebRtcClient(
                            appContext = context.applicationContext,
                            eglBase = eglBase,
                            iceServers = ice,
                            onLocalTrack = { track -> localRenderer?.let { track.addSink(it) } },
                            onRemoteTrack = { track -> remoteRenderer?.let { track.addSink(it) } },
                            onIceCandidate = { c -> s.sendSignal(targetPeerId, WebRtcClient.iceToJson(c)) },
                            onConnectionState = { st -> status = "Состояние: $st" },
                            onDataChannel = { dc -> dataChannel = dc }
                        )
                    }

                    // If there is already a peer in room, act as initiator.
                    if (targetPeerId != null) {
                        status = "Создаю offer…"
                        webrtc?.createOffer { desc ->
                            s.sendSignal(targetPeerId, WebRtcClient.sdpToJson(desc))
                        }
                    } else {
                        status = "Ожидание второго участника…"
                    }
                }

                is SignalingEvent.PeerJoined -> {
                    if (targetPeerId == null) targetPeerId = ev.peerId
                    status = "Peer подключился"
                    if (webrtc != null && targetPeerId == ev.peerId) {
                        // Become initiator if we were waiting.
                        webrtc?.createOffer { desc ->
                            s.sendSignal(targetPeerId, WebRtcClient.sdpToJson(desc))
                        }
                    }
                }

                is SignalingEvent.Signal -> {
                    val payload: JSONObject = ev.payload
                    val w = webrtc
                    if (w == null) return@collectLatest
                    if (targetPeerId == null) targetPeerId = ev.from

                    val sdp = WebRtcClient.jsonToSdp(payload)
                    if (sdp != null) {
                        w.setRemoteDescription(sdp)
                        if (sdp.type == SessionDescription.Type.OFFER) {
                            status = "Пришел offer, отвечаю…"
                            w.createAnswer { ans ->
                                s.sendSignal(targetPeerId, WebRtcClient.sdpToJson(ans))
                            }
                        }
                    }

                    val ice: IceCandidate? = WebRtcClient.jsonToIce(payload)
                    if (ice != null) w.addIceCandidate(ice)
                }

                is SignalingEvent.PeerLeft -> {
                    status = "Peer вышел"
                    targetPeerId = null
                }

                is SignalingEvent.Error -> status = "Ошибка: ${ev.code}"
                SignalingEvent.Closed -> status = "Отключено"
            }
        }
    }

    LaunchedEffect(dataChannel) {
        val dc = dataChannel ?: return@LaunchedEffect
        val c = DataChannelChat(dc).also {
            chat = it
            it.start()
        }
        c.messages.collectLatest { msg ->
            messages.add(msg)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Text("Room: $roomId")
            Text("You: ${peerId.take(8)}…")
            Text(status)
            Spacer(Modifier.height(12.dp))

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                factory = {
                    SurfaceViewRenderer(it).apply {
                        init(eglBase.eglBaseContext, null)
                        setMirror(false)
                        remoteRenderer = this
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AndroidView(
                    modifier = Modifier.size(120.dp, 160.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                    factory = {
                        SurfaceViewRenderer(it).apply {
                            init(eglBase.eglBaseContext, null)
                            setMirror(true)
                            localRenderer = this
                        }
                    }
                )

                Column(horizontalAlignment = Alignment.End) {
                    Row {
                        OutlinedButton(onClick = {
                            micOn = !micOn
                            webrtc?.toggleMic(micOn)
                        }) { Text(if (micOn) "Mic ON" else "Mic OFF") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            camOn = !camOn
                            webrtc?.toggleCamera(camOn)
                        }) { Text(if (camOn) "Cam ON" else "Cam OFF") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onHangup) { Text("Завершить") }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(if (chat != null) "Чат (P2P)" else "Чат: подключение…")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            ) {
                val last = messages.takeLast(8)
                for (m in last) {
                    Text(
                        text = (if (m.direction == ChatMessage.Direction.Out) "Ты: " else "Друг: ") + m.text,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("Сообщение") },
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = chat != null && messageText.isNotBlank(),
                    onClick = {
                        val text = messageText.trim()
                        messageText = ""
                        scope.launch { chat?.sendText(text) }
                    }
                ) { Text("Отпр.") }
            }
        }
    }
}


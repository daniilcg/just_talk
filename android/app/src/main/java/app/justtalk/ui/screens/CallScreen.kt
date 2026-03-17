package app.justtalk.ui.screens

import android.Manifest
import android.media.AudioManager
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.justtalk.core.config.UrlValidators
import app.justtalk.core.chat.ChatMessage
import app.justtalk.core.chat.DataChannelChat
import app.justtalk.core.signaling.SignalingClient
import app.justtalk.core.signaling.SignalingEvent
import app.justtalk.core.webrtc.WebRtcClient
import app.justtalk.data.ProfileStore
import app.justtalk.ui.theme.JustTalkBackground
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
import org.webrtc.VideoTrack

@Composable
fun CallScreen(
    roomId: String,
    isVideo: Boolean = true,
    onHangup: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { ProfileStore(context) }
    val scope = rememberCoroutineScope()

    var hasPerms by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Подготовка…") }
    var micOn by remember { mutableStateOf(true) }
    var camOn by remember { mutableStateOf(isVideo) }
    var speakerOn by remember { mutableStateOf(false) }

    val permsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val mic = (result[Manifest.permission.RECORD_AUDIO] == true)
        val cam = if (isVideo) (result[Manifest.permission.CAMERA] == true) else true
        hasPerms = mic && cam
    }

    LaunchedEffect(Unit) {
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cam = if (isVideo) ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED else true
        hasPerms = mic && cam
        if (!hasPerms) {
            val req = if (isVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)
            permsLauncher.launch(req)
        }
    }

    if (!hasPerms) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (isVideo) "Нужны разрешения на микрофон и камеру." else "Нужно разрешение на микрофон.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                val req = if (isVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)
                permsLauncher.launch(req)
            }) {
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
    var pendingLocalTrack: VideoTrack? by remember { mutableStateOf(null) }
    var pendingRemoteTrack: VideoTrack? by remember { mutableStateOf(null) }
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
            val am = context.getSystemService(AudioManager::class.java)
            runCatching { am?.mode = AudioManager.MODE_NORMAL }
            runCatching { am?.isSpeakerphoneOn = false }
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
        val stored = store.signalingUrl.first()
        val url = UrlValidators.normalizeSignalingUrl(stored)
        if (url == null) {
            status = "Ошибка: неверный адрес сервера звонков"
            signaling = null
            return@LaunchedEffect
        }

        status = "Подключение к сигналингу…"
        signaling = SignalingClient(url = url, roomId = roomId, peerId = peerId).also {
            runCatching { it.connect() }.onFailure { e ->
                status = "Ошибка: ws_failure (${e.message ?: "connect"})"
            }
        }
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
                            videoEnabled = isVideo,
                            onLocalTrack = { track ->
                                pendingLocalTrack = track
                                localRenderer?.let { track.addSink(it) }
                            },
                            onRemoteTrack = { track ->
                                pendingRemoteTrack = track
                                remoteRenderer?.let { track.addSink(it) }
                            },
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

    JustTalkBackground {
        Column(modifier = Modifier.fillMaxSize().imePadding().padding(14.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Звонок", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Комната: $roomId", style = MaterialTheme.typography.bodyMedium)
                    Text("Ты: ${peerId.take(8)}…", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Main media area has bounded height so controls stay visible on small screens.
            if (isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 520.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            SurfaceViewRenderer(it).apply {
                                init(eglBase.eglBaseContext, null)
                                setMirror(false)
                                remoteRenderer = this
                            }
                        },
                        update = { r ->
                            // Attach track even if it arrived before renderer.
                            val t = pendingRemoteTrack
                            if (t != null) t.addSink(r)
                        }
                    )

                    AndroidView(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(120.dp, 160.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
                        factory = {
                            SurfaceViewRenderer(it).apply {
                                init(eglBase.eglBaseContext, null)
                                setMirror(true)
                                localRenderer = this
                            }
                        },
                        update = { r ->
                            val t = pendingLocalTrack
                            if (t != null) t.addSink(r)
                        }
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Аудио звонок", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Controls bar (always visible)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = {
                            speakerOn = !speakerOn
                            val am = context.getSystemService(AudioManager::class.java)
                            runCatching { am?.mode = AudioManager.MODE_IN_COMMUNICATION }
                            runCatching { am?.isSpeakerphoneOn = speakerOn }
                        }) { Text(if (speakerOn) "Speaker" else "Earpiece") }

                        OutlinedButton(onClick = {
                            micOn = !micOn
                            webrtc?.toggleMic(micOn)
                        }) { Text(if (micOn) "Mic ON" else "Mic OFF") }

                        if (isVideo) {
                            OutlinedButton(onClick = {
                                camOn = !camOn
                                webrtc?.toggleCamera(camOn)
                            }) { Text(if (camOn) "Cam ON" else "Cam OFF") }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onHangup,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Положить трубку") }
                }
            }

            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(if (chat != null) "Чат во время звонка" else "Чат: подключение…", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f), RoundedCornerShape(14.dp))
                            .padding(10.dp)
                    ) {
                        val last = messages.takeLast(8)
                        for (m in last) {
                            Text(
                                text = (if (m.direction == ChatMessage.Direction.Out) "Ты: " else "Друг: ") + m.text,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Column {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = messageText,
                            onValueChange = { messageText = it },
                            label = { Text("Сообщение") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            modifier = Modifier.fillMaxWidth(),
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
    }
}


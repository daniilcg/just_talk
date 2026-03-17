package app.justtalk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.justtalk.core.directory.DirectoryClient
import app.justtalk.core.directory.DirectoryEvent
import app.justtalk.data.FriendsStore
import app.justtalk.data.ProfileStore
import app.justtalk.data.SecurePasswordStore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartCall: (roomId: String) -> Unit
) {
    val context = LocalContext.current
    val store = remember { ProfileStore(context) }
    val secure = remember { SecurePasswordStore(context) }
    val friendsStore = remember { FriendsStore(context) }
    val scope = rememberCoroutineScope()

    var peerId by remember { mutableStateOf("") }
    var uid by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var signalingUrl by remember { mutableStateOf("") }
    var roomId by remember { mutableStateOf("") } // manual mode fallback

    var directory: DirectoryClient? by remember { mutableStateOf(null) }
    var directoryStatus by remember { mutableStateOf("Подключение…") }

    var friendQuery by remember { mutableStateOf("") } // uid or nickname
    var lookedUp by remember { mutableStateOf(false) }
    var foundUid by remember { mutableStateOf<String?>(null) }
    var foundOnlinePeerId by remember { mutableStateOf<String?>(null) }
    var friendAddedStatus by remember { mutableStateOf<String?>(null) }

    var friends by remember { mutableStateOf<List<String>>(emptyList()) }

    var incomingInvite by remember { mutableStateOf<Pair<String, String>?>(null) } // fromPeerId, roomId

    LaunchedEffect(Unit) {
        peerId = store.ensurePeerId()
        uid = store.uid.first().orEmpty()
        email = store.email.first().orEmpty()
        nickname = store.nickname.first().orEmpty()
        signalingUrl = store.signalingUrl.first()

        directory = DirectoryClient(signalingUrl).also { it.connect() }
    }

    LaunchedEffect(Unit) {
        friendsStore.friends.collectLatest { friends = it }
    }

    DisposableEffect(Unit) {
        onDispose { directory?.close() }
    }

    LaunchedEffect(directory, uid, peerId) {
        val d = directory ?: return@LaunchedEffect
        if (uid.isNotBlank() && peerId.isNotBlank()) {
            val password = secure.getPassword().orEmpty()
            if (password.length >= 6) d.login(uid = uid, password = password, peerId = peerId)
        }
        d.events.collectLatest { ev ->
            when (ev) {
                is DirectoryEvent.LoginOk -> {
                    directoryStatus = "Онлайн"
                    // Upload FCM token for offline call invites
                    FirebaseMessaging.getInstance().token
                        .addOnSuccessListener { token ->
                            if (!token.isNullOrBlank()) d.setFcmToken(token)
                        }
                }
                is DirectoryEvent.LookupUidResult -> {
                    foundUid = ev.uid
                    foundOnlinePeerId = ev.onlinePeerId
                }
                is DirectoryEvent.LookupNicknameResult -> {
                    foundUid = ev.uid
                    foundOnlinePeerId = ev.onlinePeerId
                }
                is DirectoryEvent.Invite -> incomingInvite = ev.fromPeerId to ev.roomId
                is DirectoryEvent.InviteResult -> {
                    directoryStatus = if (ev.ok) "Инвайт отправлен" else "Инвайт: ${ev.reason ?: "ошибка"}"
                }
                DirectoryEvent.SetFcmTokenOk -> Unit
                is DirectoryEvent.Error -> directoryStatus = "Ошибка: ${ev.code}"
                DirectoryEvent.Closed -> directoryStatus = "Отключено"
                else -> Unit
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("JustTalk") })
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Ты: $nickname")
            Spacer(Modifier.height(6.dp))
            Text("UID: $uid")
            Spacer(Modifier.height(6.dp))
            if (email.isNotBlank()) {
                Text("Email: $email")
                Spacer(Modifier.height(6.dp))
            }
            Text("PeerId: ${peerId.take(8)}…")
            Spacer(Modifier.height(6.dp))
            Text("Signaling: $signalingUrl")
            Spacer(Modifier.height(18.dp))

            Text("Статус: $directoryStatus")
            Spacer(Modifier.height(14.dp))

            Text("Найти друга по UID или никнейму (он должен быть онлайн):")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = friendQuery,
                onValueChange = { friendQuery = it.trim() },
                label = { Text("UID (0000001) или никнейм") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = friendQuery.length >= 3,
                    onClick = {
                        lookedUp = true
                        foundUid = null
                        foundOnlinePeerId = null
                        val q = friendQuery
                        if (q.length == 7 && q.all { it.isDigit() }) directory?.lookupUid(q)
                        else directory?.lookupNickname(q)
                    }
                ) { Text("Поиск") }
                Spacer(Modifier.width(12.dp))
                Button(
                    enabled = friendQuery.length >= 3 && foundUid != null && foundOnlinePeerId != null,
                    onClick = {
                        val newRoom = UUID.randomUUID().toString().substring(0, 8)
                        directory?.inviteUid(fromPeerId = peerId, toUid = foundUid!!, roomId = newRoom)
                        onStartCall(newRoom)
                    }
                ) { Text("Позвонить") }
            }
            if (foundUid != null) {
                Spacer(Modifier.height(8.dp))
                val online = foundOnlinePeerId != null
                Text("Найден UID: $foundUid" + if (online) " (онлайн)" else " (оффлайн)")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        enabled = foundUid != null,
                        onClick = {
                            val u = foundUid ?: return@OutlinedButton
                            scope.launch {
                                friendsStore.add(u)
                                friendAddedStatus = "Добавлено: $u"
                            }
                        }
                    ) { Text("Добавить в друзья") }
                    if (friendAddedStatus != null) {
                        Spacer(Modifier.width(12.dp))
                        Text(friendAddedStatus!!)
                    }
                }
            } else if (lookedUp) {
                Spacer(Modifier.height(8.dp))
                Text("Не найден (или оффлайн).")
            } else {
                Spacer(Modifier.height(8.dp))
                Text("Нажми “Поиск”.")
            }

            Spacer(Modifier.height(26.dp))
            Text("Друзья:")
            Spacer(Modifier.height(8.dp))
            if (friends.isEmpty()) {
                Text("Пока пусто. Добавь друга по UID.")
            } else {
                Column {
                    for (f in friends.take(8)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("UID: $f")
                            Row {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch { directory?.lookupUid(f) }
                                    }
                                ) { Text("Статус") }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val newRoom = UUID.randomUUID().toString().substring(0, 8)
                                        directory?.inviteUid(fromPeerId = peerId, toUid = f, roomId = newRoom)
                                        onStartCall(newRoom)
                                    }
                                ) { Text("Звонок") }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = { scope.launch { friendsStore.remove(f) } }
                                ) { Text("Удалить") }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Ручной режим (если надо):")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = roomId,
                onValueChange = { roomId = it.trim() },
                label = { Text("Room ID") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { roomId = UUID.randomUUID().toString().substring(0, 8) }
                ) { Text("Сгенерировать") }
                Spacer(Modifier.width(12.dp))
                Button(
                    enabled = roomId.isNotBlank(),
                    onClick = { onStartCall(roomId) }
                ) { Text("Позвонить") }
            }
            Spacer(Modifier.height(24.dp))
            Text("Если поиск по никнейму не работает — проверь, что оба телефона онлайн и подключены к одному signaling URL.")
        }
    }

    val invite = incomingInvite
    if (invite != null) {
        AlertDialog(
            onDismissRequest = { incomingInvite = null },
            title = { Text("Входящий звонок") },
            text = { Text("Друг приглашает в комнату: ${invite.second}") },
            confirmButton = {
                Button(onClick = {
                    val room = invite.second
                    incomingInvite = null
                    onStartCall(room)
                }) { Text("Принять") }
            },
            dismissButton = {
                OutlinedButton(onClick = { incomingInvite = null }) { Text("Отклонить") }
            }
        )
    }
}


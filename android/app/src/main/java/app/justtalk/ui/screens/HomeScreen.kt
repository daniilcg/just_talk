package app.justtalk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.justtalk.core.config.RemoteConfig
import app.justtalk.core.config.UrlValidators
import app.justtalk.core.directory.DirectoryClient
import app.justtalk.core.directory.DirectoryEvent
import app.justtalk.core.directory.DirectorySession
import app.justtalk.data.FriendsStore
import app.justtalk.data.ProfileStore
import app.justtalk.data.SecurePasswordStore
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import app.justtalk.ui.theme.JustTalkBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartCall: (roomId: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChat: (uid: String) -> Unit = {},
    session: DirectorySession? = null
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

    var directoryStatus by remember { mutableStateOf("Подключение…") }

    var friendQuery by remember { mutableStateOf("") } // uid or nickname
    var lookedUp by remember { mutableStateOf(false) }
    var foundUid by remember { mutableStateOf<String?>(null) }
    var foundOnlinePeerId by remember { mutableStateOf<String?>(null) }
    var friendAddedStatus by remember { mutableStateOf<String?>(null) }

    var friends by remember { mutableStateOf<List<String>>(emptyList()) }

    var refreshingServer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        peerId = store.ensurePeerId()
        uid = store.uid.first().orEmpty()
        email = store.email.first().orEmpty()
        nickname = store.nickname.first().orEmpty()
        val saved = store.signalingUrl.first()
        val (remote, remoteErr) = withContext(Dispatchers.IO) { RemoteConfig.fetchDebug() }
        val resolved = remote?.signalingUrl ?: saved
        signalingUrl = resolved
        directoryStatus = session?.status ?: "Подключение…"
    }

    fun refreshServerNow() {
        if (refreshingServer) return
        scope.launch {
            refreshingServer = true
            directoryStatus = "Обновляем сервер…"
            try {
                val saved = store.signalingUrl.first()
                val (remote, remoteErr) = withContext(Dispatchers.IO) { RemoteConfig.fetchDebug() }
                val resolved = remote?.signalingUrl ?: saved
                signalingUrl = resolved

                if (UrlValidators.isValidSignalingUrl(resolved)) {
                    store.setSignalingUrl(resolved)
                    directoryStatus = "Подключение…"
                } else {
                    directoryStatus = "Сервер не настроен (${remoteErr ?: "no_config"})"
                }
            } finally {
                refreshingServer = false
            }
        }
    }

    LaunchedEffect(Unit) {
        friendsStore.friends.collectLatest { friends = it }
    }

    LaunchedEffect(session) {
        val s = session ?: return@LaunchedEffect
        directoryStatus = s.status
        s.events.collectLatest { ev ->
            when (ev) {
                is DirectoryEvent.LoginOk -> {
                    directoryStatus = "Онлайн"
                    val firebaseReady = FirebaseApp.getApps(context).isNotEmpty()
                    if (firebaseReady) {
                        runCatching {
                            FirebaseMessaging.getInstance().token
                                .addOnSuccessListener { token ->
                                    if (!token.isNullOrBlank()) {
                                        // Keep optional; session currently doesn't expose setFcmToken
                                    }
                                }
                        }
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
                is DirectoryEvent.InviteResult -> {
                    directoryStatus = if (ev.ok) "Инвайт отправлен" else "Инвайт: ${ev.reason ?: "ошибка"}"
                }
                is DirectoryEvent.Error -> directoryStatus = "Ошибка: ${ev.code}"
                DirectoryEvent.Closed -> directoryStatus = "Отключено"
                else -> Unit
            }
        }
    }

    JustTalkBackground {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("JustTalk") },
            actions = {
                TextButton(enabled = !refreshingServer, onClick = { refreshServerNow() }) {
                    Text("Обновить сервер")
                }
                TextButton(onClick = onOpenSettings) { Text("Настройки") }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Контакты", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("Ты: $nickname  •  UID: $uid", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(directoryStatus, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (!UrlValidators.isValidSignalingUrl(signalingUrl)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Сервер сейчас недоступен.", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Нажми “Обновить сервер” или открой настройки.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Button(onClick = { refreshServerNow() }) { Text("Обновить сервер") }
                            Spacer(Modifier.width(12.dp))
                            OutlinedButton(onClick = onOpenSettings) { Text("Настройки") }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Spacer(Modifier.height(18.dp))
            }

            // Search
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Поиск", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = friendQuery,
                        onValueChange = { friendQuery = it },
                        label = { Text("UIN / ник") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            enabled = friendQuery.trim().length >= 3 && session != null,
                            onClick = {
                                lookedUp = true
                                foundUid = null
                                foundOnlinePeerId = null
                                friendAddedStatus = null
                                val q = friendQuery.trim().lowercase()
                                session?.lookupUid(q)
                            }
                        ) { Text("Найти") }
                        Spacer(Modifier.width(12.dp))
                        if (foundUid != null) {
                            OutlinedButton(
                                onClick = {
                                    val u = foundUid ?: return@OutlinedButton
                                    scope.launch {
                                        friendsStore.add(u)
                                        friendAddedStatus = "Добавлено"
                                    }
                                }
                            ) { Text("Добавить") }
                        }
                    }
                    if (lookedUp) {
                        Spacer(Modifier.height(8.dp))
                        when {
                            foundUid != null -> {
                                val online = foundOnlinePeerId != null
                                Text(
                                    "Найден: $foundUid" + if (online) " (онлайн)" else " (оффлайн)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (friendAddedStatus != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(friendAddedStatus!!, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            else -> Text("Не найдено.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            Spacer(Modifier.height(18.dp))

            // Contacts list
            if (friends.isEmpty()) {
                Text("Контактов пока нет. Найди друга и нажми “Добавить”.", style = MaterialTheme.typography.bodyLarge)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(friends) { f ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp)
                                .clickable { onOpenChat(f) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(f, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(2.dp))
                                    Text("Нажми, чтобы открыть чат", style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(onClick = { session?.lookupUid(f) }) { Text("Пинг") }
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedButton(onClick = { scope.launch { friendsStore.remove(f) } }) { Text("Удалить") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}


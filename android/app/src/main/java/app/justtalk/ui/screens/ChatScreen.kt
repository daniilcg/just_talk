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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.justtalk.core.directory.DirectoryEvent
import app.justtalk.core.directory.DirectorySession
import app.justtalk.data.ChatFileStore
import app.justtalk.data.ChatLine
import app.justtalk.data.ProfileStore
import app.justtalk.data.SecurePasswordStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uid: String,
    onBack: () -> Unit,
    onStartCall: (roomId: String, isVideo: Boolean) -> Unit,
    session: DirectorySession
) {
    val context = LocalContext.current
    val store = remember { ProfileStore(context) }
    val secure = remember { SecurePasswordStore(context) }
    val fileStore = remember { ChatFileStore(context) }
    val scope = rememberCoroutineScope()

    val messages = remember { mutableStateListOf<String>() }
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Подключение…") }

    var myUid by remember { mutableStateOf("") }
    var peerId by remember { mutableStateOf("") }

    LaunchedEffect(uid) {
        messages.clear()
        messages.addAll(
            fileStore.load(uid).map { l ->
                (if (l.dir == "out") "Я: " else "Друг: ") + l.text
            }
        )
        myUid = store.uid.first().orEmpty()
        peerId = store.ensurePeerId()
    }

    LaunchedEffect(session, uid) {
        status = session.status
        session.events.collectLatest { ev ->
            when (ev) {
                is DirectoryEvent.LoginOk -> status = "Онлайн"
                is DirectoryEvent.Msg -> {
                    if (ev.fromUid.trim().lowercase() == uid.trim().lowercase()) {
                        fileStore.append(uid, ChatLine(dir = "in", text = ev.text, tsMs = ev.tsMs))
                        messages.add("Друг: ${ev.text}")
                    }
                }
                is DirectoryEvent.Error -> status = "Ошибка: ${ev.code}"
                DirectoryEvent.Closed -> status = "Отключено"
                else -> Unit
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Чат: $uid") },
            navigationIcon = {
                TextButton(onClick = onBack) { Text("Назад") }
            },
            actions = {
                TextButton(onClick = {
                    val room = UUID.randomUUID().toString().substring(0, 8)
                    // Invite friend then start call
                    session.inviteUid(toUid = uid, roomId = room)
                    onStartCall(room, false)
                }) { Text("Аудио") }
                TextButton(onClick = {
                    val room = UUID.randomUUID().toString().substring(0, 8)
                    session.inviteUid(toUid = uid, roomId = room)
                    onStartCall(room, true)
                }) { Text("Видео") }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(status)
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(messages) { m ->
                    Text(m)
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Сообщение") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    enabled = text.isNotBlank(),
                    onClick = {
                        val t = text.trim()
                        text = ""
                        scope.launch {
                            fileStore.append(uid, ChatLine(dir = "out", text = t, tsMs = System.currentTimeMillis()))
                            messages.add("Я: $t")
                            session.sendMsg(toUid = uid, text = t)
                        }
                    }
                ) {
                    Text("Отправить")
                }
            }
        }
    }
}


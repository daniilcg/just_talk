package app.justtalk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.justtalk.core.config.RemoteConfig
import app.justtalk.core.directory.DirectoryClient
import app.justtalk.core.directory.DirectoryEvent
import app.justtalk.data.ProfileStore
import app.justtalk.data.SecurePasswordStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { ProfileStore(context) }
    val secure = remember { SecurePasswordStore(context) }
    val scope = rememberCoroutineScope()

    var nickname by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var signalingUrl by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var turnUrl by remember { mutableStateOf("") }
    var turnUser by remember { mutableStateOf("") }
    var turnPass by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var configHint by remember { mutableStateOf<String?>(null) }
    var showTurn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val uid = store.uid.first()
        val n = store.nickname.first()
        val e = store.email.first()
        val url = store.signalingUrl.first()
        val tUrl = store.turnUrl.first()
        val tUser = store.turnUser.first()
        val tPass = store.turnPass.first()
        if (!uid.isNullOrBlank()) {
            onDone()
            return@LaunchedEffect
        }
        nickname = n.orEmpty()
        email = e.orEmpty()
        // Auto-config from GitHub raw JSON for "friends don't configure anything"
        val (remote, remoteErr) = withContext(Dispatchers.IO) { RemoteConfig.fetchDebug() }
        val resolvedUrl = remote?.signalingUrl ?: url
        signalingUrl = resolvedUrl
        configHint = when {
            remote?.signalingUrl?.startsWith("ws") == true -> "Автоконфиг: OK"
            remoteErr != null -> "Автоконфиг: $remoteErr"
            else -> "Автоконфиг: нет"
        }
        if (resolvedUrl.startsWith("ws")) {
            store.setSignalingUrl(resolvedUrl)
        }
        turnUrl = tUrl.orEmpty()
        turnUser = tUser.orEmpty()
        turnPass = tPass.orEmpty()
        ready = true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("JustTalk") },
            scrollBehavior = androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior(
                rememberTopAppBarState()
            )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Регистрация: сервер выдаст UID (как UIN)")
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = nickname,
                onValueChange = { nickname = it.trim() },
                label = { Text("Никнейм (min 3)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                singleLine = true,
                enabled = ready
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = email,
                onValueChange = { email = it },
                label = { Text("Email (опционально)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                enabled = ready
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль (min 6)") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                enabled = ready
            )
            Spacer(Modifier.height(12.dp))
            val serverLabel = if (signalingUrl.startsWith("ws")) signalingUrl else "не настроен"
            Text("Сервер: $serverLabel")
            configHint?.let {
                Spacer(Modifier.height(6.dp))
                Text(it)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = signalingUrl,
                onValueChange = { signalingUrl = it.trim() },
                label = { Text("Signaling URL (wss://...)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                enabled = ready
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { showTurn = !showTurn }, enabled = ready) {
                Text(if (showTurn) "Скрыть TURN" else "Показать TURN (если не соединяется)")
            }
            if (showTurn) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = turnUrl,
                    onValueChange = { turnUrl = it },
                    label = { Text("TURN URL (turn:host:3478)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    enabled = ready
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = turnUser,
                    onValueChange = { turnUser = it },
                    label = { Text("TURN user") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    singleLine = true,
                    enabled = ready
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = turnPass,
                    onValueChange = { turnPass = it },
                    label = { Text("TURN pass") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    enabled = ready
                )
            }
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text("Ошибка: $error")
            }
            Spacer(Modifier.height(20.dp))
            Button(
                enabled = ready && nickname.length >= 3 && password.length >= 6 && signalingUrl.startsWith("ws"),
                onClick = {
                    scope.launch {
                        store.setSignalingUrl(signalingUrl)
                        val peerId = store.ensurePeerId()

                        val client = DirectoryClient(signalingUrl).also { it.connect() }
                        client.signup(nickname = nickname, email = email.ifBlank { null }, peerId = peerId, password = password)

                        val ev = withTimeoutOrNull(8000) {
                            client.events.first { it is DirectoryEvent.SignupOk || it is DirectoryEvent.Error }
                        }
                        client.close()

                        when (ev) {
                            is DirectoryEvent.SignupOk -> {
                                store.setUid(ev.uid)
                                store.setNickname(ev.nickname)
                                if (!ev.email.isNullOrBlank()) store.setEmail(ev.email)
                                secure.setPassword(password)
                                if (turnUrl.isNotBlank() && turnUser.isNotBlank() && turnPass.isNotBlank()) {
                                    store.setTurn(turnUrl, turnUser, turnPass)
                                }
                                error = null
                                onDone()
                            }
                            is DirectoryEvent.Error -> error = ev.code
                            else -> error = "timeout"
                        }
                    }
                }
            ) {
                Text("Зарегистрироваться")
            }
        }
    }
}


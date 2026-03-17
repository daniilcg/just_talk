package app.justtalk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.justtalk.core.config.UrlValidators
import app.justtalk.data.ProfileStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ProfileStore(context) }
    val scope = rememberCoroutineScope()

    var signalingUrl by remember { mutableStateOf("") }
    var turnUrl by remember { mutableStateOf("") }
    var turnUser by remember { mutableStateOf("") }
    var turnPass by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        signalingUrl = store.signalingUrl.first()
        turnUrl = store.turnUrl.first().orEmpty()
        turnUser = store.turnUser.first().orEmpty()
        turnPass = store.turnPass.first().orEmpty()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Настройки") })
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text("Дополнительные настройки (обычно не нужно трогать).")
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = signalingUrl,
                onValueChange = { signalingUrl = it.trim() },
                label = { Text("Signaling URL (wss://...)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))
            Text("TURN (если звонки/соединение не проходят через мобильные сети)")
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = turnUrl,
                onValueChange = { turnUrl = it.trim() },
                label = { Text("TURN URL (turn:host:3478)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = turnUser,
                onValueChange = { turnUser = it.trim() },
                label = { Text("TURN user") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = turnPass,
                onValueChange = { turnPass = it },
                label = { Text("TURN pass") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )

            Spacer(Modifier.height(18.dp))
            Button(
                enabled = UrlValidators.isValidSignalingUrl(signalingUrl),
                onClick = {
                    scope.launch {
                        store.setSignalingUrl(signalingUrl)
                        if (turnUrl.isNotBlank() && turnUser.isNotBlank() && turnPass.isNotBlank()) {
                            store.setTurn(turnUrl, turnUser, turnPass)
                        }
                        saved = "Сохранено"
                        onBack()
                    }
                }
            ) {
                Text("Сохранить")
            }

            if (saved != null) {
                Spacer(Modifier.height(10.dp))
                Text(saved!!)
            }
        }
    }
}


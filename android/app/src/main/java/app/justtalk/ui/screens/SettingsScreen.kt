package app.justtalk.ui.screens

import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.justtalk.core.directory.DirectorySession
import app.justtalk.core.config.UrlValidators
import app.justtalk.data.ProfileStore
import app.justtalk.ui.theme.JustTalkBackground
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, session: DirectorySession) {
    val context = LocalContext.current
    val store = remember { ProfileStore(context) }
    val scope = rememberCoroutineScope()

    var signalingUrl by remember { mutableStateOf("") }
    var turnUrl by remember { mutableStateOf("") }
    var turnUser by remember { mutableStateOf("") }
    var turnPass by remember { mutableStateOf("") }
    var uin by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<String?>(null) }

    val pickAvatar = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        avatarUri = uri?.toString()
    }

    LaunchedEffect(Unit) {
        signalingUrl = store.signalingUrl.first()
        turnUrl = store.turnUrl.first().orEmpty()
        turnUser = store.turnUser.first().orEmpty()
        turnPass = store.turnPass.first().orEmpty()
        uin = store.uid.first().orEmpty()
        displayName = store.displayName.first().orEmpty()
        bio = store.bio.first().orEmpty()
        avatarUri = store.avatarUri.first()
    }

    JustTalkBackground {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            TopAppBar(title = { Text("Настройки") })
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.Top
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Профиль", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            AndroidView(
                                modifier = Modifier
                                    .size(64.dp)
                                    .aspectRatio(1f)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        shape = CircleShape
                                    ),
                                factory = { ctx ->
                                    ImageView(ctx).apply {
                                        scaleType = ImageView.ScaleType.CENTER_CROP
                                        clipToOutline = true
                                    }
                                },
                                update = { iv ->
                                    val u = avatarUri
                                    if (u.isNullOrBlank()) {
                                        iv.setImageDrawable(null)
                                    } else {
                                        runCatching { iv.setImageURI(Uri.parse(u)) }
                                    }
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "UIN (не меняется): ${if (uin.isBlank()) "—" else uin}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(6.dp))
                                OutlinedButton(onClick = { pickAvatar.launch("image/*") }) {
                                    Text("Выбрать фото")
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Имя (отображаемое)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = bio,
                            onValueChange = { bio = it },
                            label = { Text("О себе (коротко)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Сеть (обычно не нужно)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = signalingUrl,
                            onValueChange = { signalingUrl = it.trim() },
                            label = { Text("Signaling URL") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(Modifier.height(14.dp))
                        Text("TURN (если звонки не проходят)", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = turnUrl,
                            onValueChange = { turnUrl = it.trim() },
                            label = { Text("TURN URL (turn:host:3478)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = turnUser,
                            onValueChange = { turnUser = it.trim() },
                            label = { Text("TURN user") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = turnPass,
                            onValueChange = { turnPass = it },
                            label = { Text("TURN pass") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = UrlValidators.isValidSignalingUrl(signalingUrl),
                    onClick = {
                        scope.launch {
                            store.setSignalingUrl(signalingUrl)
                            if (turnUrl.isNotBlank() && turnUser.isNotBlank() && turnPass.isNotBlank()) {
                                store.setTurn(turnUrl, turnUser, turnPass)
                            }
                            store.setProfile(displayName = displayName, bio = bio, avatarUri = avatarUri)
                            session.setProfile(displayName = displayName, bio = bio)
                            saved = "Сохранено"
                            onBack()
                        }
                    }
                ) {
                    Text("Сохранить")
                }

                if (saved != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(saved!!, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}


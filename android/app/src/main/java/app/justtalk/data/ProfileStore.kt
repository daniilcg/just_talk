package app.justtalk.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "profile")

class ProfileStore(private val context: Context) {
    private val kEmail = stringPreferencesKey("email")
    private val kNickname = stringPreferencesKey("nickname")
    private val kUid = stringPreferencesKey("uid")
    private val kPeerId = stringPreferencesKey("peer_id")
    private val kSignalingUrl = stringPreferencesKey("signaling_url")

    val email: Flow<String?> = context.dataStore.data.map { it[kEmail] }
    val nickname: Flow<String?> = context.dataStore.data.map { it[kNickname] }
    val uid: Flow<String?> = context.dataStore.data.map { it[kUid] }
    val peerId: Flow<String> = context.dataStore.data.map { it[kPeerId] ?: UUID.randomUUID().toString() }
    val signalingUrl: Flow<String> = context.dataStore.data.map { it[kSignalingUrl] ?: "ws://10.0.2.2:8080" }

    suspend fun setEmail(email: String) {
        context.dataStore.edit { it[kEmail] = email.trim() }
    }

    suspend fun setNickname(nickname: String) {
        context.dataStore.edit { it[kNickname] = nickname.trim() }
    }

    suspend fun setUid(uid: String) {
        context.dataStore.edit { it[kUid] = uid.trim() }
    }

    suspend fun ensurePeerId(): String {
        var current: String? = null
        context.dataStore.edit { prefs ->
            current = prefs[kPeerId]
            if (current.isNullOrBlank()) {
                current = UUID.randomUUID().toString()
                prefs[kPeerId] = current!!
            }
        }
        return current!!
    }

    suspend fun setSignalingUrl(url: String) {
        context.dataStore.edit { it[kSignalingUrl] = url.trim() }
    }
}


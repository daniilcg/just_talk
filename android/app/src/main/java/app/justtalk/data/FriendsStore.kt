package app.justtalk.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.friendsStore by preferencesDataStore(name = "friends")

class FriendsStore(private val context: Context) {
    private val kFriends = stringSetPreferencesKey("friends_uids")

    val friends: Flow<List<String>> =
        context.friendsStore.data.map { prefs ->
            (prefs[kFriends] ?: emptySet())
                .toList()
                .sorted()
        }

    suspend fun add(uid: String) {
        val normalized = uid.trim().lowercase()
        // New: UID == nickname handle (3..20, a-z0-9_.-). Also allow legacy 7-digit ids.
        val isLegacyNumeric = normalized.length == 7 && normalized.all { it.isDigit() }
        val isHandle = normalized.length in 3..20 && normalized.all { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' }
        if (!isLegacyNumeric && !isHandle) return
        context.friendsStore.edit { prefs ->
            val set = (prefs[kFriends] ?: emptySet()).toMutableSet()
            set.add(normalized)
            prefs[kFriends] = set
        }
    }

    suspend fun remove(uid: String) {
        val normalized = uid.trim().lowercase()
        context.friendsStore.edit { prefs ->
            val set = (prefs[kFriends] ?: emptySet()).toMutableSet()
            set.remove(normalized)
            prefs[kFriends] = set
        }
    }
}


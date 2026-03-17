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
        val normalized = uid.trim()
        if (normalized.length != 7 || !normalized.all { it.isDigit() }) return
        context.friendsStore.edit { prefs ->
            val set = (prefs[kFriends] ?: emptySet()).toMutableSet()
            set.add(normalized)
            prefs[kFriends] = set
        }
    }

    suspend fun remove(uid: String) {
        val normalized = uid.trim()
        context.friendsStore.edit { prefs ->
            val set = (prefs[kFriends] ?: emptySet()).toMutableSet()
            set.remove(normalized)
            prefs[kFriends] = set
        }
    }
}


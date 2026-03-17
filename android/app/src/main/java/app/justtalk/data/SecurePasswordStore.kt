package app.justtalk.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePasswordStore(context: Context) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        // Some devices/ROMs can crash on EncryptedSharedPreferences (keystore/provider issues).
        // We must keep the app usable: fall back to plain SharedPreferences if crypto fails.
        runCatching {
            val masterKey =
                MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

            EncryptedSharedPreferences.create(
                appContext,
                "secure_auth",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            appContext.getSharedPreferences("secure_auth_fallback", Context.MODE_PRIVATE)
        }
    }

    fun setPassword(password: String) {
        // Never crash the app on storage failure.
        runCatching { prefs.edit().putString("password", password).apply() }
    }

    fun getPassword(): String? = runCatching { prefs.getString("password", null) }.getOrNull()

    fun clear() {
        runCatching { prefs.edit().clear().apply() }
    }
}


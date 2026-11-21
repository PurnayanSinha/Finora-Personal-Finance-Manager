package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "finora_session")

class SessionPreferences(private val context: Context) {

    private object Keys {
        val ACTIVE_USER_ID = stringPreferencesKey("active_user_id")
        val LAST_THEME = stringPreferencesKey("last_theme_preference")
    }

    val activeUserIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_USER_ID]?.takeIf { it.isNotBlank() }
    }

    val fallbackThemeFlow: Flow<ThemePreference> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.LAST_THEME]
        ThemePreference.entries.firstOrNull { it.name == raw } ?: ThemePreference.SYSTEM
    }

    suspend fun setActiveUserId(userId: String?) {
        context.dataStore.edit { prefs ->
            if (userId == null) {
                prefs.remove(Keys.ACTIVE_USER_ID)
            } else {
                prefs[Keys.ACTIVE_USER_ID] = userId
            }
        }
    }

    suspend fun setFallbackTheme(theme: ThemePreference) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_THEME] = theme.name
        }
    }
}

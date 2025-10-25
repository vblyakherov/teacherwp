package com.kubyshka.teacherworkspace.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val SESSION_PREFERENCES_NAME = "teacher_session_preferences"
private val Context.dataStore by preferencesDataStore(name = SESSION_PREFERENCES_NAME)

class SessionManager(private val context: Context) {

    private val sessionKey = stringPreferencesKey("session_key")

    val sessionKeyFlow: Flow<String?> = context.dataStore.data.map { preferences: Preferences ->
        preferences[sessionKey]
    }

    suspend fun saveSessionKey(value: String) {
        context.dataStore.edit { preferences ->
            preferences[sessionKey] = value
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(sessionKey)
        }
    }
}

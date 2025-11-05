package com.kubyshka.teacherworkspace.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val SESSION_PREFERENCES_NAME = "teacher_session_preferences"
private val Context.dataStore by preferencesDataStore(name = SESSION_PREFERENCES_NAME)

class SessionManager(private val context: Context) {

    private val sessionKey = stringPreferencesKey("session_key")
    private val pinCode = stringPreferencesKey("pin_code")
    private val teacherName = stringPreferencesKey("teacher_name")
    private val coachId = intPreferencesKey("coach_id")

    val sessionKeyFlow: Flow<String?> = context.dataStore.data.map { preferences: Preferences ->
        preferences[sessionKey]
    }

    val pinCodeFlow: Flow<String?> = context.dataStore.data.map { preferences: Preferences ->
        preferences[pinCode]
    }

    val teacherNameFlow: Flow<String?> = context.dataStore.data.map { preferences: Preferences ->
        preferences[teacherName]
    }

    val coachIdFlow: Flow<Int?> = context.dataStore.data.map { preferences: Preferences ->
        preferences[coachId]
    }

    suspend fun saveSessionKey(value: String) {
        context.dataStore.edit { preferences ->
            preferences[sessionKey] = value
        }
    }

    suspend fun savePinCode(value: String) {
        context.dataStore.edit { preferences ->
            preferences[pinCode] = value
        }
    }

    suspend fun saveTeacherName(value: String) {
        context.dataStore.edit { preferences ->
            preferences[teacherName] = value
        }
    }

    suspend fun saveCoachId(value: Int) {
        context.dataStore.edit { preferences ->
            preferences[coachId] = value
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(sessionKey)
        }
    }

    suspend fun clearPinCode() {
        context.dataStore.edit { preferences ->
            preferences.remove(pinCode)
        }
    }

    suspend fun clearTeacherName() {
        context.dataStore.edit { preferences ->
            preferences.remove(teacherName)
        }
    }

    suspend fun clearCoachId() {
        context.dataStore.edit { preferences ->
            preferences.remove(coachId)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.remove(sessionKey)
            preferences.remove(pinCode)
            preferences.remove(teacherName)
            preferences.remove(coachId)
        }
    }
}

package com.clipvault.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "clipvault_settings")

/** No-limit sentinel for [SettingsStore.historyLimit]. */
const val HISTORY_LIMIT_UNLIMITED = 0

class SettingsStore(private val context: Context) {

    private object Keys {
        val PRIVATE_MODE = booleanPreferencesKey("private_mode")
        val HISTORY_LIMIT = intPreferencesKey("history_limit")
        val ROOT_CAPTURE_ENABLED = booleanPreferencesKey("root_capture_enabled")
    }

    val privateMode: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PRIVATE_MODE] ?: false }

    val historyLimit: Flow<Int> =
        context.dataStore.data.map { it[Keys.HISTORY_LIMIT] ?: DEFAULT_HISTORY_LIMIT }

    val rootCaptureEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ROOT_CAPTURE_ENABLED] ?: false }

    suspend fun setPrivateMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PRIVATE_MODE] = enabled }
    }

    suspend fun setHistoryLimit(limit: Int) {
        context.dataStore.edit { it[Keys.HISTORY_LIMIT] = limit }
    }

    suspend fun setRootCaptureEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ROOT_CAPTURE_ENABLED] = enabled }
    }

    suspend fun isPrivateModeNow(): Boolean = privateMode.first()
    suspend fun historyLimitNow(): Int = historyLimit.first()

    companion object {
        const val DEFAULT_HISTORY_LIMIT = 200
        val LIMIT_OPTIONS = listOf(50, 100, 200, 500, HISTORY_LIMIT_UNLIMITED)
    }
}

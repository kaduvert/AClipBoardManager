package com.clipvault.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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

        // Epoch-millis timestamps for the most recent private-mode ON and OFF transitions.
        // 0L means the transition has never occurred.  Both are written atomically inside
        // setPrivateMode() so recordCapture() can determine whether a given clip was
        // captured during a private-mode window without needing system_server to know
        // anything about the current setting at capture time.
        val PRIVATE_MODE_ENABLED_AT  = longPreferencesKey("private_mode_enabled_at")
        val PRIVATE_MODE_DISABLED_AT = longPreferencesKey("private_mode_disabled_at")

        // Set by ClipRepository.recordCapture() after a clip is successfully persisted.
        // Used by CaptureCoordinator (LSPosed build) to distinguish "module is loaded"
        // from "module has actually delivered at least one clip to the database".
        val LAST_CONFIRMED_CAPTURE_AT = longPreferencesKey("last_confirmed_capture_at")
    }

    val privateMode: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PRIVATE_MODE] ?: false }

    val historyLimit: Flow<Int> =
        context.dataStore.data.map { it[Keys.HISTORY_LIMIT] ?: DEFAULT_HISTORY_LIMIT }

    val lastConfirmedCaptureAt: Flow<Long> =
        context.dataStore.data.map { it[Keys.LAST_CONFIRMED_CAPTURE_AT] ?: 0L }

    suspend fun setPrivateMode(enabled: Boolean) {
        val now = System.currentTimeMillis()
        context.dataStore.edit { prefs ->
            prefs[Keys.PRIVATE_MODE] = enabled
            if (enabled) {
                prefs[Keys.PRIVATE_MODE_ENABLED_AT] = now
            } else {
                prefs[Keys.PRIVATE_MODE_DISABLED_AT] = now
            }
        }
    }

    suspend fun setHistoryLimit(limit: Int) {
        context.dataStore.edit { it[Keys.HISTORY_LIMIT] = limit }
    }

    /** Called by [com.clipvault.app.data.ClipRepository.recordCapture] after a successful save. */
    suspend fun recordConfirmedCapture() {
        context.dataStore.edit { it[Keys.LAST_CONFIRMED_CAPTURE_AT] = System.currentTimeMillis() }
    }

    suspend fun isPrivateModeNow(): Boolean = privateMode.first()
    suspend fun historyLimitNow(): Int = historyLimit.first()

    /** Epoch-millis of when private mode was most recently turned ON; 0 if never. */
    suspend fun privateModeEnabledAtNow(): Long =
        context.dataStore.data.map { it[Keys.PRIVATE_MODE_ENABLED_AT] ?: 0L }.first()

    /** Epoch-millis of when private mode was most recently turned OFF; 0 if never. */
    suspend fun privateModeDisabledAtNow(): Long =
        context.dataStore.data.map { it[Keys.PRIVATE_MODE_DISABLED_AT] ?: 0L }.first()

    suspend fun lastConfirmedCaptureAtNow(): Long = lastConfirmedCaptureAt.first()

    companion object {
        const val DEFAULT_HISTORY_LIMIT = 200
        val LIMIT_OPTIONS = listOf(50, 100, 200, 500, HISTORY_LIMIT_UNLIMITED)
    }
}

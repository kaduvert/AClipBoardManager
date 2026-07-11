package com.clipvault.app.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.app.ClipVaultApp
import com.clipvault.app.data.ClipEntry
import com.clipvault.app.data.SettingsStore
import com.clipvault.app.root.ClipboardWatcherService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ClipUiState(
    val entries: List<ClipEntry> = emptyList(),
    val query: String = "",
    val privateMode: Boolean = false,
    val historyLimit: Int = SettingsStore.DEFAULT_HISTORY_LIMIT,
    val rootCaptureEnabled: Boolean = false
)

class ClipViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as ClipVaultApp).repository
    private val clipboardManager =
        application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val query = MutableStateFlow("")

    val uiState: StateFlow<ClipUiState> = combine(
        repository.observeEntries(query),
        query,
        repository.settings.privateMode,
        repository.settings.historyLimit,
        repository.settings.rootCaptureEnabled
    ) { entries, q, privateMode, historyLimit, rootCaptureEnabled ->
        ClipUiState(
            entries = entries,
            query = q,
            privateMode = privateMode,
            historyLimit = historyLimit,
            rootCaptureEnabled = rootCaptureEnabled
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClipUiState())

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    /** User tapped a past entry: put it back on the real clipboard, mark it active, no new row. */
    fun activate(entry: ClipEntry) {
        val clip = ClipData.newPlainText("ClipVault", entry.content)
        clipboardManager.setPrimaryClip(clip)
        viewModelScope.launch { repository.activate(entry.id) }
    }

    fun setPrivateMode(enabled: Boolean) {
        viewModelScope.launch { repository.settings.setPrivateMode(enabled) }
    }

    fun setHistoryLimit(limit: Int) {
        viewModelScope.launch { repository.settings.setHistoryLimit(limit) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun deleteEntries(ids: Set<Long>) {
        viewModelScope.launch { repository.deleteEntries(ids) }
    }

    fun setRootCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.settings.setRootCaptureEnabled(enabled) }
        val app = getApplication<Application>()
        if (enabled) ClipboardWatcherService.start(app) else ClipboardWatcherService.stop(app)
    }
}

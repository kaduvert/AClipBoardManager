package com.clipvault.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

class ClipRepository(context: Context) {

    private val dao = ClipDatabase.get(context).clipDao()
    val settings = SettingsStore(context)

    /** Reactive list, already filtered by [query] when non-blank. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeEntries(queryFlow: Flow<String>): Flow<List<ClipEntry>> =
        queryFlow.flatMapLatest { query ->
            if (query.isBlank()) dao.observeAll() else dao.observeSearch(query.trim())
        }

    /**
     * Called from the ContentProvider (LSPosed hook path) or the root capture
     * service whenever the real system clipboard changes. Respects private mode
     * and the configured history limit. No-op for blank content.
     */
    suspend fun recordCapture(content: String) {
        val text = content.takeIf { it.isNotBlank() } ?: return
        if (settings.isPrivateModeNow()) return
        val limit = settings.historyLimitNow()
        dao.upsertCapture(text, limit)
    }

    /** Called when the user taps a past entry to make it the active clip. */
    suspend fun activate(id: Long) {
        dao.activateExisting(id)
    }

    suspend fun clearHistory() {
        dao.clearAll()
    }
}

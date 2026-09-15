package com.clipvault.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import java.util.concurrent.atomic.AtomicBoolean

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
     *
     * [capturedAt] is the epoch-millis timestamp at which the clip was observed in
     * system_server (LSPosed path) or in the watcher service listener (root path).
     * It defaults to now, which is correct for callers that don't need to distinguish
     * "when observed" from "when delivered".
     *
     * **Privacy-window enforcement**: if private mode was ON at [capturedAt] - i.e.,
     * the clip was observed during a private-mode window that has since been closed -
     * it is discarded here regardless of whether private mode is currently on or off.
     * This closes the race where a clip captured while paused could be saved if the
     * delivery thread was scheduled after the user turned private mode back off.
     */
    suspend fun recordCapture(content: String, capturedAt: Long = System.currentTimeMillis()) {
        val text = content.takeIf { it.isNotBlank() } ?: return

        // Primary check: currently in private mode → discard immediately.
        if (settings.isPrivateModeNow()) return

        // Secondary check: was private mode on at capture time?
        // enabledAt is when it was most recently turned ON; disabledAt when it was most
        // recently turned OFF.  If capturedAt falls in [enabledAt, disabledAt), the clip
        // was observed during a private-mode window that has since closed → discard.
        // This only covers the most recent ON/OFF cycle (storing a full history is
        // unnecessary - rapid multi-cycles within milliseconds aren't a realistic path).
        val enabledAt  = settings.privateModeEnabledAtNow()
        val disabledAt = settings.privateModeDisabledAtNow()
        if (enabledAt > 0L && disabledAt > enabledAt && capturedAt in enabledAt until disabledAt) return

        val limit = settings.historyLimitNow()
        dao.upsertCapture(text, limit)

        // Mark delivery confirmed for this session (in-memory) and persistently
        // (DataStore). CaptureCoordinator reads the in-memory flag so that status
        // resets to "unconfirmed" on every cold start - if LSPosed breaks between
        // reboots the user sees the honest intermediate state rather than stale
        // "Active" from a successful capture that happened weeks ago.
        sessionCaptureConfirmed.set(true)
        settings.recordConfirmedCapture()
    }

    /** Called when the user taps a past entry to make it the active clip. */
    suspend fun activate(id: Long) {
        dao.activateExisting(id)
    }

    suspend fun clearHistory() {
        dao.clearAll()
    }

    suspend fun deleteEntries(ids: Set<Long>) {
        if (ids.isEmpty()) return
        dao.deleteByIds(ids.toList())
    }

    companion object {
        /**
         * True if at least one clip has been successfully persisted to the database
         * during this process lifetime. Intentionally in-memory only — resets to false
         * on every cold start so that CaptureCoordinator can show the honest
         * "module loaded but not yet confirmed this session" state if LSPosed has
         * broken since the last time things were working (e.g. after an update that
         * reset the scope, or a system_server hook that now fails silently).
         *
         * Written by [ClipRepository.recordCapture] (app process, via the
         * ContentProvider) and read by CaptureCoordinator (xposed build only).
         * The companion object makes it visible across both the app's own
         * [ClipRepository] instance and the ContentProvider's separate instance
         * without requiring a shared singleton or IPC.
         */
        val sessionCaptureConfirmed = AtomicBoolean(false)
    }
}

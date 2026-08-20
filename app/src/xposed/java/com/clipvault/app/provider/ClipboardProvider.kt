package com.clipvault.app.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.clipvault.app.data.ClipRepository
import kotlinx.coroutines.runBlocking

/**
 * The only channel through which the LSPosed hook (running inside system_server,
 * a different process and a different UID) can hand newly-observed clipboard
 * text to this app. Writes require the `com.clipvault.app.permission.WRITE_CLIP`
 * signature permission - system_server always passes that check because Android
 * treats the SYSTEM uid as implicitly trusted for every permission check.
 *
 * This provider intentionally does not expose meaningful query results; it is a
 * write-only mailbox, not a general data API.
 */
class ClipboardProvider : ContentProvider() {

    private lateinit var repository: ClipRepository

    companion object {
        const val AUTHORITY = "com.clipvault.app.provider"
        const val PATH_CLIPS = "clips"
        const val COLUMN_CONTENT = "content"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_CLIPS")
    }

    override fun onCreate(): Boolean {
        // Built lazily/independently of the Application subclass: a ContentProvider's
        // onCreate() can run before Application.onCreate() has finished.
        repository = ClipRepository(context!!.applicationContext)
        return true
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val content = values?.getAsString(COLUMN_CONTENT) ?: return null
        runBlocking { repository.recordCapture(content) }
        return CONTENT_URI
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = "vnd.android.cursor.item/vnd.clipvault.clip"

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}

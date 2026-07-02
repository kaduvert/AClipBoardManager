package com.clipvault.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single clipboard history entry.
 *
 * Only one row has [isActive] = true at any time - that's the entry currently
 * sitting in the real system clipboard. New captures deactivate every other row.
 */
@Entity(tableName = "clip_entries")
data class ClipEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val timestamp: Long,
    val isActive: Boolean = false
)

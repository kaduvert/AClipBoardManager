package com.clipvault.app.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {

    @Query("SELECT * FROM clip_entries ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ClipEntry>>

    @Query("SELECT * FROM clip_entries WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun observeSearch(query: String): Flow<List<ClipEntry>>

    @Query("SELECT * FROM clip_entries WHERE content = :content LIMIT 1")
    suspend fun findByContent(content: String): ClipEntry?

    @Query("SELECT id FROM clip_entries WHERE isActive = 1 LIMIT 1")
    suspend fun activeId(): Long?

    @Insert
    suspend fun insert(entry: ClipEntry): Long

    @Query("UPDATE clip_entries SET isActive = 0")
    suspend fun clearActiveFlag()

    @Query("UPDATE clip_entries SET isActive = 1 WHERE id = :id")
    suspend fun markActive(id: Long)

    @Query("SELECT COUNT(*) FROM clip_entries")
    suspend fun count(): Int

    @Query(
        "DELETE FROM clip_entries WHERE id IN (" +
            "SELECT id FROM clip_entries ORDER BY timestamp DESC LIMIT -1 OFFSET :keep" +
            ")"
    )
    suspend fun trimBeyond(keep: Int)

    @Query("DELETE FROM clip_entries")
    suspend fun clearAll()

    @Query("DELETE FROM clip_entries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * Records a freshly-captured clip. If identical content already exists in
     * history it is simply reactivated in place - same position, same
     * timestamp - instead of being duplicated or bumped to the top. This is
     * also what makes re-selecting a past entry a no-op for the hook/provider
     * when it observes the resulting clipboard write.
     */
    @Transaction
    suspend fun upsertCapture(content: String, historyLimit: Int): Long {
        val existing = findByContent(content)
        clearActiveFlag()
        val id = if (existing != null) {
            markActive(existing.id)
            existing.id
        } else {
            val timestamp = System.currentTimeMillis()
            val newId = insert(ClipEntry(content = content, timestamp = timestamp, isActive = true))
            if (historyLimit > 0) trimBeyond(historyLimit)
            newId
        }
        return id
    }

    @Transaction
    suspend fun activateExisting(id: Long) {
        clearActiveFlag()
        markActive(id)
    }
}

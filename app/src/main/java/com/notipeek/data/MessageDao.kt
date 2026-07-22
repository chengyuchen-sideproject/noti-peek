package com.notipeek.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /** Returns the row id, or -1 if the message was a duplicate and ignored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: CapturedMessage): Long

    @Query("SELECT * FROM captured_messages ORDER BY messageTime DESC")
    fun observeAll(): Flow<List<CapturedMessage>>

    @Query(
        """
        SELECT * FROM captured_messages
        WHERE packageName = :packageName
        ORDER BY messageTime DESC
        """
    )
    fun observeByApp(packageName: String): Flow<List<CapturedMessage>>

    /** Distinct source apps we have captured, most recent first. */
    @Query(
        """
        SELECT packageName AS packageName,
               appLabel    AS appLabel,
               COUNT(*)    AS messageCount,
               MAX(messageTime) AS lastMessageTime
        FROM captured_messages
        GROUP BY packageName
        ORDER BY lastMessageTime DESC
        """
    )
    fun observeAppSummaries(): Flow<List<AppSummary>>

    @Query("UPDATE captured_messages SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("DELETE FROM captured_messages WHERE packageName = :packageName")
    suspend fun clearApp(packageName: String)

    @Query("DELETE FROM captured_messages")
    suspend fun clearAll()
}

/** A per-app rollup for the home screen. */
data class AppSummary(
    val packageName: String,
    val appLabel: String,
    val messageCount: Int,
    val lastMessageTime: Long,
)

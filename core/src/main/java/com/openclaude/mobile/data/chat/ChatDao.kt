package com.openclaude.mobile.data.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    fun messages(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    suspend fun messagesOnce(sessionId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("UPDATE messages SET text = :text, streaming = :streaming WHERE id = :id")
    suspend fun patchText(id: Long, text: String, streaming: Boolean)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId")
    suspend fun count(sessionId: String): Int

    @Query("DELETE FROM messages WHERE sessionId = :sessionId AND id NOT IN (SELECT id FROM messages WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT :keep)")
    suspend fun trimTo(sessionId: String, keep: Int)
}

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun sessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("UPDATE sessions SET updatedAt = :whenTs, title = COALESCE(:title, title) WHERE id = :id")
    suspend fun touch(id: String, whenTs: Long, title: String? = null)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM sessions WHERE id NOT IN (SELECT id FROM sessions ORDER BY updatedAt DESC LIMIT :keep)")
    suspend fun prune(keep: Int)

    @Query("DELETE FROM sessions")
    suspend fun clearAll()
}

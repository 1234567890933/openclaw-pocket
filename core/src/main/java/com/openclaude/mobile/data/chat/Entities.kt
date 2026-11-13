package com.openclaude.mobile.data.chat

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val agentCount: Int = 1,
    val providerId: String? = null,
    val pinned: Boolean = false
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("createdAt")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: Role,
    val text: String,
    val createdAt: Long,
    val streaming: Boolean = false,
    val toolName: String? = null,
    val tokenCount: Int = 0
) {
    enum class Role { USER, ASSISTANT, TOOL, SYSTEM }
}

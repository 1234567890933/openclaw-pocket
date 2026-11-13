package com.openclaude.mobile.data.chat

import com.openclaude.mobile.util.Constants
import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Chat persistence with the retention policy baked in: four sessions, four
 * hundred messages each. Pruning happens on write so the database never has a
 * chance to grow into a battery problem.
 */
class ChatRepository(
    private val chatDao: ChatDao,
    private val sessionDao: SessionDao
) {

    fun sessions(): Flow<List<SessionEntity>> = sessionDao.sessions()

    fun messages(sessionId: String): Flow<List<MessageEntity>> = chatDao.messages(sessionId)

    suspend fun createSession(title: String = "New chat"): SessionEntity {
        val now = System.currentTimeMillis()
        val session = SessionEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now
        )
        sessionDao.upsert(session)
        sessionDao.prune(Constants.MAX_SESSIONS)
        Logs.d("chat", "created session ${session.id}")
        return session
    }

    suspend fun appendUser(sessionId: String, text: String): Long {
        val id = chatDao.insert(
            MessageEntity(
                sessionId = sessionId,
                role = MessageEntity.Role.USER,
                text = text,
                createdAt = System.currentTimeMillis()
            )
        )
        sessionDao.touch(sessionId, System.currentTimeMillis(), deriveTitle(text))
        trim(sessionId)
        return id
    }

    suspend fun appendAssistantPlaceholder(sessionId: String): Long = chatDao.insert(
        MessageEntity(
            sessionId = sessionId,
            role = MessageEntity.Role.ASSISTANT,
            text = "",
            createdAt = System.currentTimeMillis(),
            streaming = true
        )
    )

    suspend fun patchAssistant(id: Long, text: String, streaming: Boolean) {
        chatDao.patchText(id, text, streaming)
    }

    suspend fun appendTool(sessionId: String, name: String, output: String): Long = chatDao.insert(
        MessageEntity(
            sessionId = sessionId,
            role = MessageEntity.Role.TOOL,
            text = output.take(4000),
            createdAt = System.currentTimeMillis(),
            toolName = name
        )
    )

    suspend fun deleteSession(id: String) = sessionDao.delete(id)

    suspend fun clearAll() = sessionDao.clearAll()

    suspend fun historyFor(sessionId: String): List<MessageEntity> = chatDao.messagesOnce(sessionId)

    private suspend fun trim(sessionId: String) {
        if (chatDao.count(sessionId) > Constants.MAX_MESSAGES_PER_SESSION) {
            chatDao.trimTo(sessionId, Constants.MAX_MESSAGES_PER_SESSION)
        }
    }

    private fun deriveTitle(firstUserMessage: String): String {
        val clean = firstUserMessage.replace(Regex("\\s+"), " ").trim()
        return if (clean.length <= 42) clean else clean.take(42).trimEnd() + "…"
    }
}

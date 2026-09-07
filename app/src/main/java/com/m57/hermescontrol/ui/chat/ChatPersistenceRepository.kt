package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.local.ChatMessageDao
import com.m57.hermescontrol.data.local.toEntity
import com.m57.hermescontrol.data.local.toUiModel
import kotlinx.coroutines.sync.Mutex

/**
 * Wraps Room DAO operations for chat message persistence.
 *
 * Extracted from ChatViewModel to separate persistence concerns from
 * UI state management and WebSocket event handling.
 */
open class ChatPersistenceRepository(
    private val dao: ChatMessageDao,
) {
    private val replacementLock = Any()
    private val sessionWriteLocks = mutableMapOf<String, SessionWriteLock>()
    private var replacementGeneration = 0L

    private class SessionWriteLock(
        val mutex: Mutex = Mutex(),
        var users: Int = 0,
    )

    private suspend fun <T> withSessionWriteLock(
        sessionId: String,
        action: suspend () -> T,
    ): T {
        val sessionLock =
            synchronized(sessionWriteLocks) {
                sessionWriteLocks.getOrPut(sessionId) { SessionWriteLock() }.also { it.users++ }
            }
        var acquired = false
        try {
            sessionLock.mutex.lock()
            acquired = true
            return action()
        } finally {
            if (acquired) sessionLock.mutex.unlock()
            synchronized(sessionWriteLocks) {
                sessionLock.users--
                if (sessionLock.users == 0) sessionWriteLocks.remove(sessionId, sessionLock)
            }
        }
    }

    fun replacementGeneration(): Long = synchronized(replacementLock) { replacementGeneration }

    fun invalidateReplacementWrites() {
        synchronized(replacementLock) { replacementGeneration++ }
    }

    /** Persist a single message for the given session. */
    suspend fun persistMessage(
        message: ChatMessage,
        sessionId: String,
    ) {
        withSessionWriteLock(sessionId) {
            dao.upsert(message.toEntity(sessionId))
        }
    }

    /** Persist multiple messages in one transaction. */
    suspend fun persistMessages(
        messages: List<ChatMessage>,
        sessionId: String,
    ) {
        withSessionWriteLock(sessionId) {
            val entities = messages.map { it.toEntity(sessionId) }
            dao.upsertAll(entities)
        }
    }

    /** Load cached messages for a session from Room. */
    suspend fun loadMessages(sessionId: String): List<ChatMessage> =
        dao.getMessagesForSession(sessionId).map { it.toUiModel() }

    suspend fun replaceMessagesIfCurrent(
        messages: List<ChatMessage>,
        sessionId: String,
        expectedGeneration: Long,
    ): Boolean =
        withSessionWriteLock(sessionId) {
            synchronized(replacementLock) {
                if (replacementGeneration != expectedGeneration) return@synchronized false
                dao.replaceMessagesForSession(sessionId, messages.map { it.toEntity(sessionId) })
                true
            }
        }
}

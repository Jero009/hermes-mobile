package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.local.ChatMessageDao
import com.m57.hermescontrol.data.local.ChatMessageEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ChatPersistenceRepositoryTest {
    @Test
    fun writeStartedBeforeReplacementCannotResurrectMessage() {
        val dao = RacingDao(blockContent = "stale")
        val repository = ChatPersistenceRepository(dao)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val staleWrite =
                executor.submit {
                    runBlocking {
                        repository.persistMessage(
                            message("stale"),
                            "session-a",
                        )
                    }
                }
            assertTrue(dao.blockedWriteStarted.await(5, TimeUnit.SECONDS))
            val replacementStarted = CountDownLatch(1)
            val replacement =
                executor.submit<Boolean> {
                    replacementStarted.countDown()
                    runBlocking {
                        repository.replaceMessagesIfCurrent(
                            listOf(message("replacement")),
                            "session-a",
                            repository.replacementGeneration(),
                        )
                    }
                }
            assertTrue(replacementStarted.await(5, TimeUnit.SECONDS))
            val replacementCompletedBeforeRelease =
                try {
                    replacement.get(1, TimeUnit.SECONDS)
                } catch (_: TimeoutException) {
                    null
                }

            dao.allowBlockedWrite.countDown()
            staleWrite.get(5, TimeUnit.SECONDS)
            assertTrue(replacementCompletedBeforeRelease ?: replacement.get(5, TimeUnit.SECONDS))

            assertEquals(listOf("replacement"), dao.contents("session-a"))
        } finally {
            dao.allowBlockedWrite.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun batchWriteStartedBeforeReplacementCannotResurrectMessages() {
        val dao = RacingDao(blockContent = "stale batch")
        val repository = ChatPersistenceRepository(dao)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val staleWrite =
                executor.submit {
                    runBlocking {
                        repository.persistMessages(listOf(message("stale batch"), message("also stale")), "session-a")
                    }
                }
            assertTrue(dao.blockedWriteStarted.await(5, TimeUnit.SECONDS))
            val replacementStarted = CountDownLatch(1)
            val replacement =
                executor.submit<Boolean> {
                    replacementStarted.countDown()
                    runBlocking {
                        repository.replaceMessagesIfCurrent(
                            listOf(message("replacement")),
                            "session-a",
                            repository.replacementGeneration(),
                        )
                    }
                }
            assertTrue(replacementStarted.await(5, TimeUnit.SECONDS))
            val replacementCompletedBeforeRelease =
                try {
                    replacement.get(1, TimeUnit.SECONDS)
                } catch (_: TimeoutException) {
                    null
                }

            dao.allowBlockedWrite.countDown()
            staleWrite.get(5, TimeUnit.SECONDS)
            assertTrue(replacementCompletedBeforeRelease ?: replacement.get(5, TimeUnit.SECONDS))

            assertEquals(listOf("replacement"), dao.contents("session-a"))
        } finally {
            dao.allowBlockedWrite.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun writeStartedAfterReplacementIsRetained() =
        runBlocking {
            val dao = RacingDao()
            val repository = ChatPersistenceRepository(dao)

            assertTrue(
                repository.replaceMessagesIfCurrent(
                    listOf(message("replacement")),
                    "session-a",
                    repository.replacementGeneration(),
                ),
            )
            repository.persistMessage(message("new message"), "session-a")

            assertEquals(listOf("replacement", "new message"), dao.contents("session-a"))
        }

    @Test
    fun blockedSessionDoesNotBlockUnrelatedSession() {
        val dao = RacingDao(blockContent = "blocked")
        val repository = ChatPersistenceRepository(dao)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val blockedWrite =
                executor.submit {
                    runBlocking {
                        repository.persistMessage(
                            message("blocked"),
                            "session-a",
                        )
                    }
                }
            assertTrue(dao.blockedWriteStarted.await(5, TimeUnit.SECONDS))

            val unrelated =
                executor.submit<Boolean> {
                    runBlocking {
                        repository.replaceMessagesIfCurrent(
                            listOf(message("other replacement")),
                            "session-b",
                            repository.replacementGeneration(),
                        )
                    }
                }
            assertTrue(unrelated.get(5, TimeUnit.SECONDS))
            assertEquals(listOf("other replacement"), dao.contents("session-b"))

            dao.allowBlockedWrite.countDown()
            blockedWrite.get(5, TimeUnit.SECONDS)
        } finally {
            dao.allowBlockedWrite.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun replacementAndInvalidationAreAtomic() {
        val dao = RacingDao(blockReplacement = true)
        val repository = ChatPersistenceRepository(dao)
        val generation = repository.replacementGeneration()
        val executor = Executors.newFixedThreadPool(2)

        try {
            val replacement =
                executor.submit<Boolean> {
                    runBlocking {
                        repository.replaceMessagesIfCurrent(listOf(message("current")), "session-a", generation)
                    }
                }
            assertTrue(dao.blockedReplacementStarted.await(5, TimeUnit.SECONDS))
            val invalidationFinished = CountDownLatch(1)
            executor.submit {
                repository.invalidateReplacementWrites()
                invalidationFinished.countDown()
            }

            assertFalse(invalidationFinished.await(100, TimeUnit.MILLISECONDS))
            dao.allowBlockedReplacement.countDown()
            assertTrue(replacement.get(5, TimeUnit.SECONDS))
            assertTrue(invalidationFinished.await(5, TimeUnit.SECONDS))
            assertFalse(
                runBlocking {
                    repository.replaceMessagesIfCurrent(listOf(message("stale")), "session-a", generation)
                },
            )
        } finally {
            dao.allowBlockedReplacement.countDown()
            executor.shutdownNow()
        }
    }

    private fun message(content: String) =
        ChatMessage(
            id = content,
            role = MessageRole.ASSISTANT,
            content = content,
        )

    private class RacingDao(
        private val blockContent: String? = null,
        private val blockReplacement: Boolean = false,
    ) : ChatMessageDao {
        private val messages = ConcurrentHashMap<String, MutableList<ChatMessageEntity>>()
        val blockedWriteStarted = CountDownLatch(1)
        val allowBlockedWrite = CountDownLatch(1)
        val blockedReplacementStarted = CountDownLatch(1)
        val allowBlockedReplacement = CountDownLatch(1)

        override suspend fun sessionExists(sessionId: String): Boolean = messages.containsKey(sessionId)

        override suspend fun getMessagesForSession(sessionId: String): List<ChatMessageEntity> =
            synchronized(messages) { messages[sessionId]?.toList().orEmpty() }

        override suspend fun upsert(message: ChatMessageEntity) {
            blockWriteIfNeeded(listOf(message))
            store(listOf(message))
        }

        override fun upsertAll(messages: List<ChatMessageEntity>) {
            blockWriteIfNeeded(messages)
            store(messages)
        }

        override fun deleteMessagesForSession(sessionId: String) {
            synchronized(messages) { messages.remove(sessionId) }
        }

        override fun replaceMessagesForSession(
            sessionId: String,
            messages: List<ChatMessageEntity>,
        ) {
            if (blockReplacement) {
                blockedReplacementStarted.countDown()
                assertTrue(allowBlockedReplacement.await(5, TimeUnit.SECONDS))
            }
            synchronized(this.messages) { this.messages[sessionId] = messages.toMutableList() }
        }

        fun contents(sessionId: String): List<String> =
            synchronized(messages) { messages[sessionId]?.map { it.content }.orEmpty() }

        private fun blockWriteIfNeeded(messages: List<ChatMessageEntity>) {
            if (messages.any { it.content == blockContent }) {
                blockedWriteStarted.countDown()
                assertTrue(allowBlockedWrite.await(5, TimeUnit.SECONDS))
            }
        }

        private fun store(newMessages: List<ChatMessageEntity>) {
            synchronized(messages) {
                newMessages.forEach { message ->
                    val sessionMessages = messages.getOrPut(message.sessionId) { mutableListOf() }
                    sessionMessages.removeAll { it.id == message.id }
                    sessionMessages += message
                }
            }
        }
    }
}

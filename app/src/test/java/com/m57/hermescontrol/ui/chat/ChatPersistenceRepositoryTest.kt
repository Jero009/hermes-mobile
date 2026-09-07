package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.local.ChatMessageDao
import com.m57.hermescontrol.data.local.ChatMessageEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ChatPersistenceRepositoryTest {
    @Test
    fun replacementAndInvalidationAreAtomic() {
        val replacementStarted = CountDownLatch(1)
        val allowReplacement = CountDownLatch(1)
        val invalidationFinished = CountDownLatch(1)
        val dao = BlockingReplacementDao(replacementStarted, allowReplacement)
        val repository = ChatPersistenceRepository(dao)
        val generation = repository.replacementGeneration()
        val executor = Executors.newFixedThreadPool(2)

        try {
            val replacement =
                executor.submit<Boolean> {
                    runBlocking {
                        repository.replaceMessagesIfCurrent(
                            listOf(ChatMessage(role = MessageRole.ASSISTANT, content = "current")),
                            "session-a",
                            generation,
                        )
                    }
                }
            assertTrue(replacementStarted.await(5, TimeUnit.SECONDS))
            executor.submit {
                repository.invalidateReplacementWrites()
                invalidationFinished.countDown()
            }

            assertFalse(invalidationFinished.await(100, TimeUnit.MILLISECONDS))
            allowReplacement.countDown()
            assertTrue(replacement.get(5, TimeUnit.SECONDS))
            assertTrue(invalidationFinished.await(5, TimeUnit.SECONDS))
            assertFalse(
                runBlocking {
                    repository.replaceMessagesIfCurrent(
                        listOf(ChatMessage(role = MessageRole.ASSISTANT, content = "stale")),
                        "session-a",
                        generation,
                    )
                },
            )
        } finally {
            allowReplacement.countDown()
            executor.shutdownNow()
        }
    }

    private class BlockingReplacementDao(
        private val started: CountDownLatch,
        private val proceed: CountDownLatch,
    ) : ChatMessageDao {
        override suspend fun sessionExists(sessionId: String): Boolean = false

        override suspend fun getMessagesForSession(sessionId: String): List<ChatMessageEntity> = emptyList()

        override suspend fun upsert(message: ChatMessageEntity) = Unit

        override fun upsertAll(messages: List<ChatMessageEntity>) = Unit

        override fun deleteMessagesForSession(sessionId: String) = Unit

        override fun replaceMessagesForSession(
            sessionId: String,
            messages: List<ChatMessageEntity>,
        ) {
            started.countDown()
            assertTrue(proceed.await(5, TimeUnit.SECONDS))
        }
    }
}

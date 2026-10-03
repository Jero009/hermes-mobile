package com.m57.hermescontrol.ui.bots

import com.m57.hermescontrol.data.bots.BotGroupRepository
import com.m57.hermescontrol.data.local.BotGroupMemberEntity
import com.m57.hermescontrol.data.local.BotGroupRoomEntity
import com.m57.hermescontrol.data.ws.SourcedWsEvent
import com.m57.hermescontrol.data.ws.WsEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The group chat room is a local projection over member sessions: sends
 * fan-out through the existing prompt.submit path only to resolved members,
 * reply events merge per owning bot, and a connection-profile change fences
 * both directions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BotGroupChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: BotGroupRepository
    private lateinit var events: MutableSharedFlow<SourcedWsEvent>
    private val sentPrompts = mutableListOf<Pair<String, String>>()

    private val room = BotGroupRoomEntity("r1", "p1", "Squad", 1L, 1L)
    private val members =
        listOf(
            BotGroupMemberEntity("r1", "alpha", "session-alpha"),
            BotGroupMemberEntity("r1", "beta", null),
            BotGroupMemberEntity("r1", "gamma", "session-gamma"),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        events = MutableSharedFlow(extraBufferCapacity = 16)
        sentPrompts.clear()
        coEvery { repository.room("r1") } returns room
        coEvery { repository.members("r1") } returns members
        coEvery { repository.messages("r1") } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        selectedProfile: () -> String? = { "p1" },
        sendMessage: (String, String) -> Unit = { sessionId, text -> sentPrompts.add(sessionId to text) },
    ): BotGroupChatViewModel =
        BotGroupChatViewModel(
            roomId = "r1",
            repository = repository,
            ioDispatcher = dispatcher,
            selectedConnectionProfileId = selectedProfile,
            events = events,
            sendMessage = sendMessage,
        )

    @Test
    fun `opening a room loads it with membership and persisted transcript`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(room, viewModel.uiState.value.room)
            assertEquals(
                listOf("alpha", "beta", "gamma"),
                viewModel.uiState.value.members.map { it.botName },
            )
            assertTrue(viewModel.uiState.value.messages.isEmpty())
            assertFalse(viewModel.uiState.value.fenced)
            assertTrue(viewModel.uiState.value.hasUnresolvedMembers)
        }

    @Test
    fun `send fans out only to resolved member sessions and records the user entry`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.send("hello group")
            advanceUntilIdle()

            assertEquals(
                listOf("session-alpha" to "hello group", "session-gamma" to "hello group"),
                sentPrompts,
            )
            val last = viewModel.uiState.value.messages.last()
            assertEquals("user", last.sender)
            assertEquals("hello group", last.content)
            coVerify(exactly = 1) { repository.appendEntry("r1", any()) }
        }

    @Test
    fun `a profile change mid fan-out stops remaining members and fences the room`() =
        runTest(dispatcher) {
            var selected = "p1"
            val viewModel =
                viewModel(
                    selectedProfile = { selected },
                    sendMessage = { sessionId, text ->
                        sentPrompts.add(sessionId to text)
                        // The first dispatch flips the active connection profile.
                        selected = "p2"
                    },
                )
            advanceUntilIdle()

            viewModel.send("hello")
            advanceUntilIdle()

            assertEquals(listOf("session-alpha" to "hello"), sentPrompts)
            assertTrue(viewModel.uiState.value.fenced)
            // The user entry from the partially delivered fan-out is retained.
            coVerify(exactly = 1) { repository.appendEntry("r1", any()) }
        }

    @Test
    fun `message complete events for member sessions merge as their bot`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            events.emit(
                SourcedWsEvent(
                    event = WsEvent.MessageComplete("group answer", "session-alpha"),
                    profileId = "p1",
                    connectionGeneration = 1,
                ),
            )
            advanceUntilIdle()

            val merged = viewModel.uiState.value.messages.single()
            assertEquals("alpha", merged.sender)
            assertEquals("group answer", merged.content)
            coVerify(exactly = 1) { repository.appendEntry("r1", any()) }
        }

    @Test
    fun `events from other profiles or unknown sessions never merge`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            events.emit(
                SourcedWsEvent(
                    event = WsEvent.MessageComplete("foreign", "session-alpha"),
                    profileId = "p2",
                    connectionGeneration = 1,
                ),
            )
            events.emit(
                SourcedWsEvent(
                    event = WsEvent.MessageComplete("stray", "other-session"),
                    profileId = "p1",
                    connectionGeneration = 1,
                ),
            )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.messages.isEmpty())
            coVerify(exactly = 0) { repository.appendEntry(any(), any()) }
        }

    @Test
    fun `an active profile change fences the room and blocks further sends`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onActiveProfileChanged("p2")

            assertTrue(viewModel.uiState.value.fenced)
            viewModel.send("should not dispatch")
            advanceUntilIdle()

            assertTrue(sentPrompts.isEmpty())
            coVerify(exactly = 0) { repository.appendEntry(any(), any()) }
        }

    @Test
    fun `a missing room fences immediately`() =
        runTest(dispatcher) {
            coEvery { repository.room("ghost") } returns null
            val viewModel =
                BotGroupChatViewModel(
                    roomId = "ghost",
                    repository = repository,
                    ioDispatcher = dispatcher,
                    selectedConnectionProfileId = { "p1" },
                    events = events,
                    sendMessage = { _, _ -> sentPrompts.add("x" to "y") },
                )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.fenced)
            viewModel.send("nothing")
            advanceUntilIdle()
            assertTrue(sentPrompts.isEmpty())
        }
}

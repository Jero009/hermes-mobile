package com.m57.hermescontrol.ui.bots

import com.m57.hermescontrol.data.bots.BotGroupRepository
import com.m57.hermescontrol.data.bots.BotGroupRosterValidity
import com.m57.hermescontrol.data.local.BotGroupRoomEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** Room-list state on the Bots screen: profile-scoped, policy-checked CRUD. */
@OptIn(ExperimentalCoroutinesApi::class)
class BotGroupsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: BotGroupRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(selectedProfile: () -> String? = { "p1" }) =
        BotGroupsViewModel(
            repository = repository,
            ioDispatcher = dispatcher,
            selectedConnectionProfileId = selectedProfile,
            autoLoad = false,
        )

    private fun room(
        id: String,
        profileId: String = "p1",
    ) = BotGroupRoomEntity(id, profileId, "Group $id", 1L, 1L)

    @Test
    fun `room list loads only for the selected connection profile`() =
        runTest(dispatcher) {
            coEvery { repository.rooms("p1") } returns listOf(room("r1"), room("r2"))

            val viewModel = viewModel()
            viewModel.loadRooms()
            advanceUntilIdle()

            assertEquals(listOf("r1", "r2"), viewModel.uiState.value.rooms.map { it.id })
            assertEquals("p1", viewModel.uiState.value.sourceConnectionProfileId)
            coVerify(exactly = 1) { repository.rooms("p1") }
            coVerify(exactly = 0) { repository.rooms("p2") }
        }

    @Test
    fun `creating a room with a valid roster persists and refreshes the list`() =
        runTest(dispatcher) {
            coEvery { repository.rooms("p1") } returns listOf(room("new-room"))
            coEvery {
                repository.createRoom("p1", "Squad", listOf("alpha" to "sa", "beta" to null))
            } returns room("new-room")

            val viewModel = viewModel()
            viewModel.loadRooms()
            advanceUntilIdle()
            val created =
                viewModel.createRoom(
                    title = "Squad",
                    members = listOf("alpha" to "sa", "beta" to null),
                )
            advanceUntilIdle()

            assertTrue(created)
            assertEquals(listOf("new-room"), viewModel.uiState.value.rooms.map { it.id })
        }

    @Test
    fun `rosters outside policy are rejected before storage and surfaced to the ui`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            assertEquals(BotGroupRosterValidity.TOO_FEW, viewModel.validateRoster(listOf("alpha" to "sa")))
            assertEquals(
                BotGroupRosterValidity.TOO_MANY,
                viewModel.validateRoster(listOf("a", "b", "c", "d", "e", "f", "g").map { it to null }),
            )
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.createRoom(any(), any(), any()) }
        }

    @Test
    fun `creating a room requires a selected connection profile`() =
        runTest(dispatcher) {
            val viewModel = viewModel(selectedProfile = { null })

            val created =
                viewModel.createRoom(title = "Squad", members = listOf("alpha" to null, "beta" to null))
            advanceUntilIdle()

            assertFalse(created)
            coVerify(exactly = 0) { repository.createRoom(any(), any(), any()) }
        }

    @Test
    fun `deleting a room removes it from the visible list`() =
        runTest(dispatcher) {
            val rooms = mutableListOf(room("r1"))
            coEvery { repository.room("r1") } returns room("r1")
            coEvery { repository.rooms("p1") } answers { rooms.toList() }
            coEvery { repository.deleteRoom("r1") } answers {
                rooms.clear()
                Unit
            }
            val viewModel = viewModel()
            viewModel.loadRooms()
            advanceUntilIdle()

            viewModel.deleteRoom("r1")
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.deleteRoom("r1") }
            assertTrue(viewModel.uiState.value.rooms.isEmpty())
        }

    @Test
    fun `deleting a room is fenced to the room's owning connection profile`() =
        runTest(dispatcher) {
            coEvery { repository.room("r2") } returns room("r2", profileId = "p2")

            viewModel().deleteRoom("r2")
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.deleteRoom("r2") }
            coVerify(exactly = 0) { repository.rooms(any()) }
        }
}

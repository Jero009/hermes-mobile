package com.m57.hermescontrol.data.bots

import com.m57.hermescontrol.data.local.BotGroupDao
import com.m57.hermescontrol.data.local.BotGroupMemberEntity
import com.m57.hermescontrol.data.local.BotGroupMessageEntity
import com.m57.hermescontrol.data.local.BotGroupRoomEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Group rooms are profile-scoped durable state: the room row carries the
 * connection profile that created it, membership stores each bot's resolved
 * session, and the notification-exclusion index mirrors resolved member
 * sessions per profile.
 */
class BotGroupRepositoryTest {
    private lateinit var dao: BotGroupDao

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        BotGroupSessionIndex.resetForTest()
    }

    @After
    fun tearDown() {
        BotGroupSessionIndex.resetForTest()
    }

    private fun repository(clock: () -> Long = { 1_000L }) =
        BotGroupRepository(
            dao = dao,
            newId = { "id-${clock().toInt()}" },
            clock = clock,
        )

    @Test
    fun `createRoom persists a profile-scoped room with member sessions`() =
        runTest {
            val repo = repository()
            val roomSlot = slot<BotGroupRoomEntity>()
            val memberSlot = slot<List<BotGroupMemberEntity>>()
            coEvery { dao.insertRoom(capture(roomSlot)) } returns Unit
            coEvery { dao.insertMembers(capture(memberSlot)) } returns Unit

            val room = repo.createRoom("p1", "Squad", listOf("alpha" to "sa", "beta" to null))

            assertEquals("p1", roomSlot.captured.connectionProfileId)
            assertEquals(room.id, roomSlot.captured.id)
            assertEquals("Squad", room.title)
            assertEquals(
                listOf(
                    BotGroupMemberEntity(room.id, "alpha", "sa"),
                    BotGroupMemberEntity(room.id, "beta", null),
                ),
                memberSlot.captured,
            )
        }

    @Test
    fun `createRoom refuses rosters outside policy without touching storage`() =
        runTest {
            val repo = repository()

            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking { repo.createRoom("p1", "Solo", listOf("alpha" to "sa")) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    repo.createRoom("p1", "Crowd", listOf("a", "b", "c", "d", "e", "f", "g").map { it to null })
                }
            }
            coVerify(exactly = 0) { dao.insertRoom(any()) }
            coVerify(exactly = 0) { dao.insertMembers(any()) }
        }

    @Test
    fun `rooms are listed for the owning connection profile only`() =
        runTest {
            val rooms = listOf(BotGroupRoomEntity("r1", "p1", "Squad", 1L, 1L))
            coEvery { dao.roomsForProfile("p1") } returns rooms

            assertEquals(rooms, repository().rooms("p1"))
            coVerify(exactly = 1) { dao.roomsForProfile("p1") }
        }

    @Test
    fun `publishIndex exposes only resolved member session ids per profile`() =
        runTest {
            coEvery { dao.roomsForProfile("p1") } returns listOf(BotGroupRoomEntity("r1", "p1", "Squad", 1L, 1L))
            coEvery { dao.membersForRoom("r1") } returns
                listOf(
                    BotGroupMemberEntity("r1", "alpha", "sa"),
                    BotGroupMemberEntity("r1", "beta", null),
                )

            repository().publishIndex("p1")

            assertEquals(setOf("sa"), BotGroupSessionIndex.sessionsForProfile("p1"))
            assertEquals(emptySet<String>(), BotGroupSessionIndex.sessionsForProfile("p2"))
        }

    @Test
    fun `deleting a room drops it from the index after republish`() =
        runTest {
            coEvery { dao.roomsForProfile("p1") } returns emptyList()

            repository().publishIndex("p1")

            assertEquals(emptySet<String>(), BotGroupSessionIndex.sessionsForProfile("p1"))
        }

    @Test
    fun `creating a room republishes the exclusion index for its profile`() =
        runTest {
            val rooms = mutableListOf<BotGroupRoomEntity>()
            val members = mutableListOf<BotGroupMemberEntity>()
            coEvery { dao.insertRoom(any()) } answers { rooms.add(firstArg()) }
            coEvery { dao.insertMembers(any()) } answers { members.addAll(firstArg()) }
            coEvery { dao.roomsForProfile("p1") } answers { rooms.toList() }
            coEvery { dao.membersForRoom(any()) } answers { members.filter { it.roomId == firstArg<String>() } }

            repository().createRoom("p1", "Squad", listOf("alpha" to "sa", "beta" to null))

            assertEquals(setOf("sa"), BotGroupSessionIndex.sessionsForProfile("p1"))
        }

    @Test
    fun `deleting a room republishes the index from the profile read before the delete`() =
        runTest {
            val rooms = mutableListOf(BotGroupRoomEntity("r1", "p1", "Squad", 1L, 1L))
            val members = mutableListOf(BotGroupMemberEntity("r1", "alpha", "sa"))
            coEvery { dao.roomById("r1") } answers { rooms.firstOrNull { it.id == "r1" } }
            coEvery { dao.deleteRoom("r1") } answers { rooms.clear() }
            coEvery { dao.roomsForProfile("p1") } answers { rooms.toList() }
            coEvery { dao.membersForRoom(any()) } answers { members.filter { it.roomId == firstArg<String>() } }
            val repo = repository()
            repo.publishIndex("p1")
            assertEquals(setOf("sa"), BotGroupSessionIndex.sessionsForProfile("p1"))

            repo.deleteRoom("r1")

            assertEquals(emptySet<String>(), BotGroupSessionIndex.sessionsForProfile("p1"))
        }

    @Test
    fun `loading rooms repopulates the exclusion index after process restart`() =
        runTest {
            coEvery { dao.roomsForProfile("p1") } returns listOf(BotGroupRoomEntity("r1", "p1", "Squad", 1L, 1L))
            coEvery { dao.membersForRoom("r1") } returns listOf(BotGroupMemberEntity("r1", "alpha", "sa"))
            assertEquals(emptySet<String>(), BotGroupSessionIndex.sessionsForProfile("p1"))

            repository().rooms("p1")

            assertEquals(setOf("sa"), BotGroupSessionIndex.sessionsForProfile("p1"))
        }

    @Test
    fun `appendEntry stores the transcript entry verbatim`() =
        runTest {
            val repo = repository()
            val messageSlot = slot<BotGroupMessageEntity>()
            coEvery { dao.insertMessage(capture(messageSlot)) } returns Unit
            val entry = GroupTranscript.userEntry("r1", "hello", 42L)

            repo.appendEntry("r1", entry)

            with(messageSlot.captured) {
                assertEquals(entry.id, id)
                assertEquals("r1", roomId)
                assertEquals("user", sender)
                assertTrue(fromUser)
                assertEquals("hello", content)
                assertEquals(42L, timestamp)
            }
        }

    @Test
    fun `recordMemberSession updates membership and republishes the index`() =
        runTest {
            val members =
                mutableListOf(BotGroupMemberEntity("r1", "beta", "sb"))
            coEvery { dao.roomById("r1") } returns BotGroupRoomEntity("r1", "p1", "Squad", 1L, 1L)
            coEvery { dao.roomsForProfile("p1") } returns listOf(BotGroupRoomEntity("r1", "p1", "Squad", 1L, 1L))
            coEvery { dao.membersForRoom("r1") } answers { members.toList() }
            coEvery { dao.updateMemberSession("r1", "beta", "sb-new") } answers {
                members[0] = members[0].copy(resolvedSessionId = "sb-new")
                Unit
            }

            repository().recordMemberSession("r1", "beta", "sb-new")

            coVerify(exactly = 1) { dao.updateMemberSession("r1", "beta", "sb-new") }
            coVerify(exactly = 1) { dao.touchRoom("r1", any()) }
            assertEquals(setOf("sb-new"), BotGroupSessionIndex.sessionsForProfile("p1"))
        }

    @Test
    fun `recording a session for a member of a missing room does nothing`() =
        runTest {
            coEvery { dao.roomById("ghost") } returns null

            repository().recordMemberSession("ghost", "beta", "sb")

            coVerify(exactly = 0) { dao.updateMemberSession(any(), any(), any()) }
            assertNull(repository().room("ghost"))
        }
}

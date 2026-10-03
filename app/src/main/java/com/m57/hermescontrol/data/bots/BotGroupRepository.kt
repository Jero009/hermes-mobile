package com.m57.hermescontrol.data.bots

import com.m57.hermescontrol.data.local.BotGroupDao
import com.m57.hermescontrol.data.local.BotGroupMemberEntity
import com.m57.hermescontrol.data.local.BotGroupMessageEntity
import com.m57.hermescontrol.data.local.BotGroupRoomEntity
import java.util.UUID

/**
 * Durable, profile-scoped storage for local phone group chats (v1).
 *
 * The repository is the only writer of group tables and the only publisher of
 * [BotGroupSessionIndex], so the notification exclusion can never drift from
 * what membership actually resolves to.
 */
class BotGroupRepository(
    private val dao: BotGroupDao,
    private val index: BotGroupSessionIndex = BotGroupSessionIndex,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun rooms(profileId: String): List<BotGroupRoomEntity> {
        val loaded = dao.roomsForProfile(profileId)
        refreshIndex(profileId, loaded)
        return loaded
    }

    suspend fun room(roomId: String): BotGroupRoomEntity? = dao.roomById(roomId)

    suspend fun members(roomId: String): List<BotGroupMemberEntity> = dao.membersForRoom(roomId)

    suspend fun messages(roomId: String): List<BotGroupMessageEntity> = dao.messagesForRoom(roomId)

    /**
     * Create a room with its member sessions. Roster policy is enforced here
     * as a backstop so no UI path can persist an out-of-policy roster.
     */
    suspend fun createRoom(
        profileId: String,
        title: String,
        members: List<Pair<String, String?>>,
    ): BotGroupRoomEntity {
        require(profileId.isNotBlank()) { "Group rooms require a connection profile" }
        require(BotGroupPolicy.validate(members.map { it.first }) == BotGroupRosterValidity.OK) {
            "Group roster is outside the 2–6 distinct-member policy"
        }
        val now = clock()
        val room =
            BotGroupRoomEntity(
                id = newId(),
                connectionProfileId = profileId,
                title = title.trim().ifBlank { "Group" },
                createdAt = now,
                updatedAt = now,
            )
        dao.insertRoom(room)
        dao.insertMembers(
            members.map { (botName, sessionId) ->
                BotGroupMemberEntity(
                    roomId = room.id,
                    botName = botName,
                    resolvedSessionId = sessionId?.takeIf(String::isNotBlank),
                )
            },
        )
        publishIndex(profileId)
        return room
    }

    /** Deletes the room row; membership and messages fall to the FK cascade. */
    suspend fun deleteRoom(roomId: String) {
        val profileId = dao.roomById(roomId)?.connectionProfileId
        dao.deleteRoom(roomId)
        profileId?.let { publishIndex(it) }
    }

    /** Persist a merged transcript entry verbatim (stable ids make this idempotent). */
    suspend fun appendEntry(
        roomId: String,
        entry: GroupTranscriptEntry,
    ) {
        dao.insertMessage(
            BotGroupMessageEntity(
                id = entry.id,
                roomId = roomId,
                sender = entry.sender,
                fromUser = entry.fromUser,
                content = entry.content,
                sourceSessionId = entry.sourceSessionId,
                timestamp = entry.timestamp,
            ),
        )
        dao.touchRoom(roomId, clock())
    }

    /** Record a newly resolved member session and keep the exclusion index fresh. */
    suspend fun recordMemberSession(
        roomId: String,
        botName: String,
        sessionId: String,
    ) {
        val room = dao.roomById(roomId) ?: return
        dao.updateMemberSession(roomId, botName, sessionId)
        dao.touchRoom(roomId, clock())
        publishIndex(room.connectionProfileId)
    }

    /**
     * Republish every resolved member session of [profileId] into the
     * notification-exclusion index.
     */
    suspend fun publishIndex(profileId: String) {
        refreshIndex(profileId, dao.roomsForProfile(profileId))
    }

    private suspend fun refreshIndex(
        profileId: String,
        rooms: List<BotGroupRoomEntity>,
    ) {
        val sessionIds = mutableSetOf<String>()
        for (room in rooms) {
            for (member in dao.membersForRoom(room.id)) {
                member.resolvedSessionId?.takeIf(String::isNotBlank)?.let(sessionIds::add)
            }
        }
        index.replaceProfile(profileId, sessionIds)
    }
}

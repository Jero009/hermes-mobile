package com.m57.hermescontrol.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A local phone group chat room (v1). Rooms are durable, profile-scoped
 * state: [connectionProfileId] is the connection profile that created the
 * room, and rooms of other profiles are never listed or opened.
 */
@Entity(tableName = "bot_group_rooms")
data class BotGroupRoomEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "connection_profile_id")
    val connectionProfileId: String,
    val title: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

/**
 * One roster bot in a group room. [resolvedSessionId] is the locally resolved
 * member session (canonical or managed-title) used for fan-out; null means
 * the member has no session yet and is skipped on send.
 */
@Entity(
    tableName = "bot_group_members",
    primaryKeys = ["room_id", "bot_name"],
    foreignKeys = [
        ForeignKey(
            entity = BotGroupRoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["room_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("room_id")],
)
data class BotGroupMemberEntity(
    @ColumnInfo(name = "room_id")
    val roomId: String,
    @ColumnInfo(name = "bot_name")
    val botName: String,
    @ColumnInfo(name = "resolved_session_id")
    val resolvedSessionId: String? = null,
)

/**
 * One merged line of a group transcript. Local group state only — group
 * messages never travel through the server's session history.
 */
@Entity(
    tableName = "bot_group_messages",
    foreignKeys = [
        ForeignKey(
            entity = BotGroupRoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["room_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["room_id", "timestamp"])],
)
data class BotGroupMessageEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "room_id")
    val roomId: String,
    /** `"user"` or the owning bot's profile name. */
    val sender: String,
    @ColumnInfo(name = "from_user")
    val fromUser: Boolean,
    val content: String,
    @ColumnInfo(name = "source_session_id")
    val sourceSessionId: String? = null,
    val timestamp: Long,
)

@Dao
interface BotGroupDao {
    @Query("SELECT * FROM bot_group_rooms WHERE connection_profile_id = :profileId ORDER BY updated_at DESC")
    suspend fun roomsForProfile(profileId: String): List<BotGroupRoomEntity>

    @Query("SELECT * FROM bot_group_rooms WHERE id = :roomId")
    suspend fun roomById(roomId: String): BotGroupRoomEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRoom(room: BotGroupRoomEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMembers(members: List<BotGroupMemberEntity>)

    @Query("DELETE FROM bot_group_rooms WHERE id = :roomId")
    suspend fun deleteRoom(roomId: String)

    @Query("UPDATE bot_group_rooms SET updated_at = :updatedAt WHERE id = :roomId")
    suspend fun touchRoom(
        roomId: String,
        updatedAt: Long,
    )

    @Query("SELECT * FROM bot_group_members WHERE room_id = :roomId ORDER BY bot_name ASC")
    suspend fun membersForRoom(roomId: String): List<BotGroupMemberEntity>

    @Query(
        "UPDATE bot_group_members SET resolved_session_id = :sessionId " +
            "WHERE room_id = :roomId AND bot_name = :botName",
    )
    suspend fun updateMemberSession(
        roomId: String,
        botName: String,
        sessionId: String,
    )

    @Query("SELECT * FROM bot_group_messages WHERE room_id = :roomId ORDER BY timestamp ASC, id ASC")
    suspend fun messagesForRoom(roomId: String): List<BotGroupMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: BotGroupMessageEntity)
}

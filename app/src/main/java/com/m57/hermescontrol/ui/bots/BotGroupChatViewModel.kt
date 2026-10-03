package com.m57.hermescontrol.ui.bots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.bots.BotGroupRepository
import com.m57.hermescontrol.data.bots.GroupFanOut
import com.m57.hermescontrol.data.bots.GroupMemberTarget
import com.m57.hermescontrol.data.bots.GroupTranscript
import com.m57.hermescontrol.data.bots.GroupTranscriptEntry
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.local.BotGroupMessageEntity
import com.m57.hermescontrol.data.local.BotGroupRoomEntity
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.data.ws.SourcedWsEvent
import com.m57.hermescontrol.data.ws.WsEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Transient send outcome the UI renders as a snackbar. */
enum class GroupSendNotice {
    UNRESOLVED_MEMBERS,
    SEND_FAILED,
    PROFILE_CHANGED,
}

data class BotGroupMemberUi(
    val botName: String,
    val resolvedSessionId: String?,
)

data class BotGroupChatUiState(
    val room: BotGroupRoomEntity? = null,
    val members: List<BotGroupMemberUi> = emptyList(),
    val messages: List<GroupTranscriptEntry> = emptyList(),
    val isSending: Boolean = false,
    /** True when the active connection profile no longer owns this room. */
    val fenced: Boolean = false,
    val notice: GroupSendNotice? = null,
) {
    val hasUnresolvedMembers: Boolean
        get() = members.any { it.resolvedSessionId.isNullOrBlank() }
}

/**
 * Local phone group chat (v1): a durable, profile-scoped local projection
 * over per-bot sessions. Sends fan-out through the existing `prompt.submit`
 * path to each resolved member session; gateway replies merge into the local
 * transcript per owning bot. There is no backend group API.
 *
 * Fencing is bidirectional: sends re-check profile currency before every
 * dispatch, and incoming events are admitted only when the delivering
 * connection profile is the room's own.
 */
internal class BotGroupChatViewModel(
    private val roomId: String,
    private val repository: BotGroupRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val selectedConnectionProfileId: () -> String? = AuthManager::getSelectedProfileId,
    events: SharedFlow<SourcedWsEvent> = HermesWsClient.sourcedEvents,
    private val sendMessage: (sessionId: String, text: String) -> Unit = HermesWsClient::sendMessage,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BotGroupChatUiState())
    val uiState: StateFlow<BotGroupChatUiState> = _uiState.asStateFlow()

    /** Member session id → bot name, for merging reply events. */
    private var membersBySessionId: Map<String, String> = emptyMap()

    init {
        viewModelScope.launch(ioDispatcher) { loadRoom() }
        viewModelScope.launch(ioDispatcher) {
            events.collect { sourced -> onSourcedEvent(sourced) }
        }
    }

    private suspend fun loadRoom() {
        val room = repository.room(roomId)
        if (room == null || selectedConnectionProfileId() != room.connectionProfileId) {
            fence()
            return
        }
        val members = repository.members(roomId)
        membersBySessionId =
            members
                .mapNotNull { member -> member.resolvedSessionId?.let { it to member.botName } }
                .toMap()
        val messages = repository.messages(roomId).map { it.toEntry() }
        _uiState.update {
            it.copy(
                room = room,
                members = members.map { m -> BotGroupMemberUi(m.botName, m.resolvedSessionId) },
                messages = messages,
            )
        }
        repository.publishIndex(room.connectionProfileId)
    }

    private suspend fun onSourcedEvent(sourced: SourcedWsEvent) {
        val complete = sourced.event as? WsEvent.MessageComplete ?: return
        val room = _uiState.value.room ?: return
        // Event provenance comes from the delivering connection, never from
        // the currently selected profile.
        if (sourced.profileId != room.connectionProfileId) return
        if (selectedConnectionProfileId() != room.connectionProfileId) {
            fence()
            return
        }
        val entry = GroupTranscript.entryForEvent(complete, membersBySessionId, clock(), roomId) ?: return
        repository.appendEntry(roomId, entry)
        _uiState.update { it.copy(messages = it.messages + entry) }
    }

    /** Screen-driven fence: the selected connection profile changed while the room is open. */
    fun onActiveProfileChanged(profileId: String?) {
        val room = _uiState.value.room ?: return
        if (profileId != room.connectionProfileId) fence()
    }

    private fun fence() {
        _uiState.update { it.copy(fenced = true) }
    }

    fun clearNotice() = _uiState.update { it.copy(notice = null) }

    /**
     * Fan the text out to every resolved member session. The user entry is
     * recorded when at least one member was dispatched; a profile change mid
     * fan-out fences the room and stops the remaining members.
     */
    fun send(text: String) {
        val trimmed = text.trim()
        val state = _uiState.value
        val room = state.room ?: return
        if (trimmed.isEmpty() || state.fenced || state.isSending) return
        if (selectedConnectionProfileId() != room.connectionProfileId) {
            fence()
            return
        }
        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch(ioDispatcher) {
            val targets =
                _uiState.value.members.map { member ->
                    GroupMemberTarget(member.botName, member.resolvedSessionId)
                }
            val report =
                GroupFanOut.execute(
                    members = targets,
                    isProfileCurrent = { selectedConnectionProfileId() == room.connectionProfileId },
                    text = trimmed,
                ) { _, sessionId, textToSend -> sendMessage(sessionId, textToSend) }

            if (report.dispatchedAny) {
                val entry = GroupTranscript.userEntry(roomId, trimmed, clock())
                repository.appendEntry(roomId, entry)
                _uiState.update { it.copy(messages = it.messages + entry) }
            }
            _uiState.update {
                it.copy(
                    isSending = false,
                    fenced = it.fenced || report.fencedAny,
                    notice =
                        when {
                            report.fencedAny -> GroupSendNotice.PROFILE_CHANGED
                            report.unresolvedBots.isNotEmpty() -> GroupSendNotice.UNRESOLVED_MEMBERS
                            !report.dispatchedAny -> GroupSendNotice.SEND_FAILED
                            else -> null
                        },
                )
            }
        }
    }

    /** Re-resolve a member's session once the roster knows it. */
    fun recordMemberSession(
        botName: String,
        sessionId: String,
    ) {
        if (sessionId.isBlank()) return
        viewModelScope.launch(ioDispatcher) {
            repository.recordMemberSession(roomId, botName, sessionId)
            val members = repository.members(roomId)
            membersBySessionId =
                members
                    .mapNotNull { member -> member.resolvedSessionId?.let { it to member.botName } }
                    .toMap()
            _uiState.update {
                it.copy(members = members.map { m -> BotGroupMemberUi(m.botName, m.resolvedSessionId) })
            }
        }
    }

    private fun BotGroupMessageEntity.toEntry(): GroupTranscriptEntry =
        GroupTranscriptEntry(
            id = id,
            sender = sender,
            fromUser = fromUser,
            content = content,
            timestamp = timestamp,
            sourceSessionId = sourceSessionId,
        )
}

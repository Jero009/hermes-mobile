package com.m57.hermescontrol.ui.bots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.bots.BotGroupPolicy
import com.m57.hermescontrol.data.bots.BotGroupRepository
import com.m57.hermescontrol.data.bots.BotGroupRosterValidity
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.local.BotGroupRoomEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Group room list state on the Bots screen. */
data class BotGroupsUiState(
    val isLoading: Boolean = false,
    val rooms: List<BotGroupRoomEntity> = emptyList(),
    val sourceConnectionProfileId: String? = null,
    val rosterError: BotGroupRosterValidity? = null,
)

/**
 * Owns the local phone group chat room list for the selected connection
 * profile: durable rooms live in Room, scoped per profile, and every write is
 * fenced on the profile that was selected when the user acted.
 */
class BotGroupsViewModel(
    private val repository: BotGroupRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val selectedConnectionProfileId: () -> String? = AuthManager::getSelectedProfileId,
    autoLoad: Boolean = true,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BotGroupsUiState())
    val uiState: StateFlow<BotGroupsUiState> = _uiState.asStateFlow()

    init {
        if (autoLoad) loadRooms()
    }

    fun loadRooms() {
        val profileId = selectedConnectionProfileId()
        viewModelScope.launch(ioDispatcher) {
            val rooms = profileId?.let { repository.rooms(it) }.orEmpty()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    rooms = rooms,
                    sourceConnectionProfileId = profileId,
                )
            }
        }
    }

    fun validateRoster(members: List<Pair<String, String?>>): BotGroupRosterValidity =
        BotGroupPolicy.validate(members.map { it.first })

    /**
     * Create a room for the currently selected connection profile. Out-of-policy
     * rosters are rejected before storage; a profile change between tap and
     * write fences the creation.
     */
    fun createRoom(
        title: String,
        members: List<Pair<String, String?>>,
    ): Boolean {
        val profileId = selectedConnectionProfileId()
        val validity = validateRoster(members)
        if (profileId.isNullOrBlank() || validity != BotGroupRosterValidity.OK) {
            _uiState.update { it.copy(rosterError = validity.takeIf { v -> v != BotGroupRosterValidity.OK }) }
            return false
        }
        viewModelScope.launch(ioDispatcher) {
            if (selectedConnectionProfileId() != profileId) return@launch
            runCatching { repository.createRoom(profileId, title, members) }
            loadRooms()
        }
        return true
    }

    fun deleteRoom(roomId: String) {
        val profileId = selectedConnectionProfileId()
        viewModelScope.launch(ioDispatcher) {
            val room = repository.room(roomId)
            if (room?.connectionProfileId != profileId) return@launch
            repository.deleteRoom(roomId)
            if (selectedConnectionProfileId() == profileId) loadRooms()
        }
    }

    fun clearRosterError() = _uiState.update { it.copy(rosterError = null) }
}

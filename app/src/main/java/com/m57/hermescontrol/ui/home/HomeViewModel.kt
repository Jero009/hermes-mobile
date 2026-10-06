package com.m57.hermescontrol.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.bots.filterManagedBotSessions
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.data.model.SessionInfo
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val isLoading: Boolean = false,
    val profiles: List<ProfileInfo> = emptyList(),
    val activeProfileName: String? = null,
    val activeProfileId: String? = null,
    val sessions: List<SessionInfo> = emptyList(),
    val errorMessage: String? = null,
)

class HomeViewModel(
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val activeResponse =
                    withContext(ioDispatcher) {
                        safeApiCall { ApiClient.hermesApi.getActiveProfile() }
                    }
                val profilesResult =
                    withContext(ioDispatcher) {
                        safeApiCall { ApiClient.hermesApi.getProfiles() }
                    }
                val sessionsResult =
                    withContext(ioDispatcher) {
                        safeApiCall {
                            ApiClient.hermesApi.getSessions(
                                limit = 2,
                                offset = 0,
                                order = "recent",
                                excludeSources = "automation",
                            )
                        }
                    }

                val profiles = (profilesResult as? NetworkResult.Success)?.data?.profiles.orEmpty()
                val activeResponseName = (activeResponse as? NetworkResult.Success)?.data?.active
                val activeName = selectActiveProfile(profiles, activeResponseName)

                val recentSessions =
                    filterManagedBotSessions(
                        (sessionsResult as? NetworkResult.Success)?.data?.sessions.orEmpty(),
                    ).take(2)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        profiles = profiles,
                        activeProfileName = activeName,
                        activeProfileId = AuthManager.getSelectedProfileId(),
                        sessions = recentSessions,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

fun selectActiveProfile(
    profiles: List<ProfileInfo>,
    activeResponseName: String?,
): String? {
    return profiles.firstOrNull { it.name == activeResponseName }?.name
        ?: profiles.firstOrNull { it.is_default == true }?.name
        ?: profiles.firstOrNull()?.name
}

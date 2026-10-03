package com.m57.hermescontrol.ui.bots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.bots.BotSessionResolution
import com.m57.hermescontrol.data.bots.BotSessionResolver
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.data.ws.HermesWsClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MAX_ACTIVE_NOW_BOTS = 12
private const val PRESENCE_WINDOW_SECONDS = 90L
private const val LOAD_ERROR_MESSAGE = "Unable to load bots"

/**
 * Locally-held gateway session identity for a roster bot the server left
 * without a canonical session. [sessionId] is present only for bots that are
 * openable; [unresolved] carries the capability-gated reason otherwise.
 */
data class BotResolutionState(
    val sessionId: String? = null,
    val unresolved: BotSessionResolution? = null,
)

data class BotsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val profiles: List<ProfileInfo> = emptyList(),
    val searchQuery: String = "",
    val showHidden: Boolean = false,
    val errorMessage: String? = null,
    val nowSeconds: Long = 0L,
    val sourceConnectionProfileId: String? = null,
    /** Async resolution results keyed by bot profile name. */
    val resolutions: Map<String, BotResolutionState> = emptyMap(),
) {
    val hasHiddenBots: Boolean
        get() = profiles.any { it.isHidden }

    val hasUnresolvedBots: Boolean
        get() = resolutions.values.any { it.sessionId == null }

    val activeNowBots: List<ProfileInfo>
        get() =
            profiles
                .filter { it.isActiveAt(nowSeconds) }
                .sortedWith(compareByDescending<ProfileInfo> { lastActive(it) }.thenBy { it.name })
                .take(MAX_ACTIVE_NOW_BOTS)

    val displayProfiles: List<ProfileInfo>
        get() {
            val query = searchQuery.trim().lowercase()
            return profiles
                .filter { showHidden || !it.isHidden }
                .filter {
                    query.isBlank() || it.name.lowercase().contains(query) ||
                        it.effectiveTitle.lowercase().contains(query) ||
                        it.effectiveDescription.lowercase().contains(query)
                }
                .sortedWith(compareByDescending<ProfileInfo> { lastActive(it) }.thenBy { it.name })
        }
}

private fun lastActive(profile: ProfileInfo): Long =
    listOfNotNull(
        profile.worker_session?.last_active,
        profile.canonical_session?.last_active,
        profile.last_session?.last_active,
    ).maxOrNull() ?: Long.MIN_VALUE

internal fun ProfileInfo.isActiveAt(nowSeconds: Long): Boolean =
    lastActive(this) >= nowSeconds - PRESENCE_WINDOW_SECONDS

internal fun BotResolutionState?.openableSessionId(): String? = this?.sessionId

private suspend fun defaultResolveBotSession(bot: ProfileInfo): BotSessionResolution =
    BotSessionResolver(
        api = ApiClient.hermesApi,
        gatewayRequest = { method, params ->
            runCatching { HermesWsClient.request(method, params).await() }
        },
    ).resolve(bot)

class BotsViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    autoLoad: Boolean = true,
    private val clockSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    private val selectedConnectionProfileId: () -> String? = AuthManager::getSelectedProfileId,
    private val resolveBotSession: suspend (ProfileInfo) -> BotSessionResolution = ::defaultResolveBotSession,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BotsUiState())
    val uiState: StateFlow<BotsUiState> = _uiState.asStateFlow()

    /** Fences async resolutions against a newer roster load. */
    private var rosterGeneration = 0

    init {
        if (autoLoad) loadBots()
    }

    fun loadBots(isRefresh: Boolean = false) {
        val sourceProfileId = selectedConnectionProfileId()
        rosterGeneration++
        _uiState.update {
            if (isRefresh) {
                it.copy(isRefreshing = true, errorMessage = null)
            } else {
                it.copy(isLoading = true, errorMessage = null)
            }
        }
        viewModelScope.launch(ioDispatcher) {
            when (val result = safeApiCall { ApiClient.hermesApi.getProfiles() }) {
                is NetworkResult.Success -> {
                    val profiles = result.data.profiles
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            profiles = profiles,
                            nowSeconds = clockSeconds(),
                            sourceConnectionProfileId = sourceProfileId,
                            resolutions = it.resolutions.filterKeys { name -> profiles.any { p -> p.name == name } },
                        )
                    }
                    resolveMissingBots(sourceProfileId)
                }

                is NetworkResult.Failure ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = LOAD_ERROR_MESSAGE,
                        )
                    }
            }
        }
    }

    /**
     * Resolve every bot the server left without a canonical session, one at a
     * time. Each result is fenced: a newer roster load or a connection-profile
     * change discards the remaining work and every not-yet-applied outcome.
     */
    private fun resolveMissingBots(sourceProfileId: String?) {
        val pending = _uiState.value.profiles.filter { it.canonicalSessionId == null }
        if (pending.isEmpty()) return
        val generation = rosterGeneration
        viewModelScope.launch(ioDispatcher) {
            for (bot in pending) {
                val resolution =
                    runCatching { resolveBotSession(bot) }
                        .getOrElse { BotSessionResolution.Failed(it.message ?: "resolution failed") }
                if (generation != rosterGeneration || selectedConnectionProfileId() != sourceProfileId) {
                    return@launch
                }
                _uiState.update {
                    it.copy(resolutions = it.resolutions + (bot.name to resolution.toResolutionState()))
                }
            }
        }
    }

    fun setSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun toggleShowHidden() = _uiState.update { it.copy(showHidden = !it.showHidden) }
}

private fun BotSessionResolution.toResolutionState(): BotResolutionState =
    when (this) {
        is BotSessionResolution.Resolved -> BotResolutionState(sessionId = sessionId)
        is BotSessionResolution.ProvisionIncomplete -> BotResolutionState(sessionId = sessionId, unresolved = this)
        is BotSessionResolution.Ambiguous,
        is BotSessionResolution.CreateUnsupported,
        is BotSessionResolution.Failed,
        -> BotResolutionState(sessionId = null, unresolved = this)
    }

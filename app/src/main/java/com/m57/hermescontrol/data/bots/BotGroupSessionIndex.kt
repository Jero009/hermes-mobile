package com.m57.hermescontrol.data.bots

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide mirror of the gateway session ids that belong to local bot
 * group memberships, scoped by connection profile. The notification service
 * consults this index so group member sessions stay out of the system shade.
 */
object BotGroupSessionIndex {
    private val _sessionsByProfile = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    /** Read-only view: connection profile → resolved member session ids. */
    val sessionsByProfile: StateFlow<Map<String, Set<String>>> = _sessionsByProfile.asStateFlow()

    fun sessionsForProfile(profileId: String?): Set<String> =
        profileId?.let { _sessionsByProfile.value[it] } ?: emptySet()

    fun replaceProfile(
        profileId: String,
        sessionIds: Set<String>,
    ) {
        if (profileId.isBlank()) return
        _sessionsByProfile.value = _sessionsByProfile.value + (profileId to sessionIds)
    }

    fun removeProfile(profileId: String) {
        _sessionsByProfile.value = _sessionsByProfile.value - profileId
    }

    /** For testing only. */
    fun resetForTest() {
        _sessionsByProfile.value = emptyMap()
    }
}

package com.m57.hermescontrol.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.BotsScreen
import com.m57.hermescontrol.ChatScreen
import com.m57.hermescontrol.NavigationController
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.ui.common.BotAvatar
import com.m57.hermescontrol.ui.common.ErrorState
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.LoadingState
import com.m57.hermescontrol.ui.common.NavIcon

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
    sessionId: String? = null,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.load()
    }

    HermesScaffold(
        modifier = modifier,
        title = { Text(stringResource(R.string.screen_home)) },
        navigationIcon = NavIcon.Menu { onOpenDrawer?.invoke() },
        drawerGesturesEnabled = true,
        onRefresh = { viewModel.load() },
        isRefreshing = state.isLoading,
    ) { paddingValues ->
        when {
            state.isLoading && state.sessions.isEmpty() && state.profiles.isEmpty() && state.errorMessage == null -> {
                LoadingState(modifier = Modifier.padding(paddingValues))
            }
            state.errorMessage != null -> {
                ErrorState(
                    message = state.errorMessage ?: "Failed to load",
                    onRetry = { viewModel.load() },
                    modifier = Modifier.padding(paddingValues),
                )
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val activeProfile =
                        state.profiles
                            .firstOrNull { it.name == state.activeProfileName }
                            ?: state.profiles.firstOrNull()
                    if (activeProfile != null) {
                        ActiveProfileCard(
                            profile = activeProfile,
                            onOpen = {
                                val selectedConnectionProfileId = AuthManager.getSelectedProfileId()
                                if (!selectedConnectionProfileId.isNullOrBlank()) {
                                    NavigationController.openBot(
                                        activeProfile,
                                        selectedConnectionProfileId,
                                        activeProfile.canonicalSessionId,
                                    )
                                }
                            },
                        )
                    }

                    val activeBots = activeProfiles(state.profiles, state.nowSeconds)
                    if (activeBots.isNotEmpty()) {
                        SectionHeader(
                            title = stringResource(R.string.home_active_conversations),
                            action = stringResource(R.string.home_view_all_bots),
                            onAction = { NavigationController.navigateTo(BotsScreen) },
                        )
                        activeBots.take(2).forEach { profile ->
                            QuickProfileRow(
                                profile = profile,
                                onClick = {
                                    val selectedConnectionProfileId = AuthManager.getSelectedProfileId()
                                    if (!selectedConnectionProfileId.isNullOrBlank() &&
                                        !profile.canonicalSessionId.isNullOrBlank()
                                    ) {
                                        NavigationController.openBot(
                                            profile,
                                            selectedConnectionProfileId,
                                            profile.canonicalSessionId,
                                        )
                                    } else {
                                        NavigationController.navigateTo(BotsScreen)
                                    }
                                },
                            )
                        }
                    }

                    val sessionsToShow = state.sessions.take(2)
                    if (sessionsToShow.isNotEmpty()) {
                        SectionHeader(title = stringResource(R.string.home_recent_chats))
                        sessionsToShow.forEach { session ->
                            val botAttribution =
                                remember(session) {
                                    val botName = session.source?.takeIf { it.isNotBlank() }
                                    if (!botName.isNullOrBlank() && session.preview != null) {
                                        botName
                                    } else {
                                        null
                                    }
                                }
                            SessionRow(
                                session = session,
                                botAttribution = botAttribution,
                                onClick = {
                                    val selectedConnectionProfileId = AuthManager.getSelectedProfileId()
                                    if (!selectedConnectionProfileId.isNullOrBlank()) {
                                        NavigationController.queuePendingSession(
                                            sessionId = session.id,
                                            profileId = selectedConnectionProfileId,
                                        )
                                        NavigationController.navigateTo(ChatScreen)
                                    }
                                },
                            )
                        }
                    }

                    SectionHeader(
                        title = stringResource(R.string.home_profiles),
                        action = stringResource(R.string.home_view_all_bots),
                        onAction = { NavigationController.navigateTo(BotsScreen) },
                    )
                    BrowseBotsRow(onClick = { NavigationController.navigateTo(BotsScreen) })

                    if (state.sessions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.home_context_empty),
                            modifier = Modifier.padding(horizontal = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveProfileCard(
    profile: ProfileInfo,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            BotAvatar(
                name = profile.effectiveTitle,
                avatar = profile.botMeta()?.avatar,
                size = 56.dp,
                isActive = true,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.effectiveTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Text(
                    text = profile.effectiveDescription.ifBlank { profile.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.TextButton(onClick = onOpen) {
                Text("Open")
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: com.m57.hermescontrol.data.model.SessionInfo,
    botAttribution: String? = null,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Dashboard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title ?: session.id,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                session.preview?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                botAttribution?.let {
                    Text(
                        text = "Bot: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickProfileRow(
    profile: ProfileInfo,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            BotAvatar(
                name = profile.effectiveTitle,
                avatar = profile.botMeta()?.avatar,
                size = 36.dp,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = profile.effectiveTitle,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BrowseBotsRow(onClick: () -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.home_browse_bots),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            androidx.compose.material3.TextButton(onClick = onAction) { Text(action) }
        }
    }
}

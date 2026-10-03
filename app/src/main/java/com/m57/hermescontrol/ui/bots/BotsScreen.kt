package com.m57.hermescontrol.ui.bots

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.BotGroupScreen
import com.m57.hermescontrol.NavigationController
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.bots.BotGroupPolicy
import com.m57.hermescontrol.data.bots.BotGroupRepository
import com.m57.hermescontrol.data.bots.BotGroupRosterValidity
import com.m57.hermescontrol.data.bots.BotSessionResolution
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.local.BotGroupRoomEntity
import com.m57.hermescontrol.data.local.HermesDatabase
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.ui.common.BotAvatar
import com.m57.hermescontrol.ui.common.ErrorState
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.LoadingState
import com.m57.hermescontrol.ui.common.NavIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotsScreen(
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
    viewModel: BotsViewModel = viewModel { BotsViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedProfileId by AuthManager.selectedProfileIdFlow.collectAsStateWithLifecycle()
    val appContext = LocalContext.current.applicationContext
    val groupsViewModel: BotGroupsViewModel =
        viewModel {
            BotGroupsViewModel(
                repository = BotGroupRepository(HermesDatabase.get(appContext).botGroupDao()),
            )
        }
    val groupsState by groupsViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(selectedProfileId, state.sourceConnectionProfileId) {
        val sourceProfileId = state.sourceConnectionProfileId
        if (sourceProfileId != null && sourceProfileId != selectedProfileId && !state.isLoading) {
            viewModel.loadBots()
        }
    }

    // Groups live on the selected connection profile too: reload when it changes.
    LaunchedEffect(selectedProfileId, groupsState.sourceConnectionProfileId) {
        val sourceProfileId = groupsState.sourceConnectionProfileId
        if (sourceProfileId != selectedProfileId) {
            groupsViewModel.loadRooms()
        }
    }

    var showCreateGroup by remember { mutableStateOf(false) }

    HermesScaffold(
        modifier = modifier,
        title = { Text(stringResource(R.string.screen_bots)) },
        navigationIcon = onOpenDrawer?.let { NavIcon.Menu(it) },
        actions = {
            if (state.hasHiddenBots) {
                IconButton(onClick = viewModel::toggleShowHidden) {
                    Icon(
                        if (state.showHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(R.string.bots_toggle_hidden),
                    )
                }
            }
        },
        isRefreshing = state.isRefreshing,
        onRefresh = {
            viewModel.loadBots(isRefresh = true)
            groupsViewModel.loadRooms()
        },
    ) { paddingValues ->
        when {
            state.isLoading && state.profiles.isEmpty() -> LoadingState(Modifier.padding(paddingValues))
            state.errorMessage != null && state.profiles.isEmpty() ->
                ErrorState(state.errorMessage.orEmpty(), onRetry = viewModel::loadBots)
            else ->
                Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(R.string.bots_search_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "groups-header") {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.bots_groups_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { showCreateGroup = true }) {
                                    Text(stringResource(R.string.bots_groups_new))
                                }
                            }
                        }
                        if (groupsState.rooms.isEmpty()) {
                            item(key = "groups-empty") {
                                Text(
                                    stringResource(R.string.bots_groups_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(groupsState.rooms, key = { "group-${it.id}" }) { room ->
                            GroupRoomRow(
                                room = room,
                                onOpen = {
                                    NavigationController.navigateTo(BotGroupScreen(roomId = room.id))
                                },
                                onDelete = { groupsViewModel.deleteRoom(room.id) },
                            )
                        }
                        items(state.displayProfiles) { profile ->
                            val resolution = state.resolutions[profile.name]
                            val canOpen =
                                canOpenBot(
                                    profile,
                                    state.sourceConnectionProfileId,
                                    selectedProfileId,
                                    resolution?.sessionId,
                                )
                            BotCard(
                                profile = profile,
                                isActive = profile.isActiveAt(state.nowSeconds),
                                unresolved = resolution?.takeIf { it.sessionId == null }?.unresolved,
                                onClick =
                                    if (canOpen) {
                                        {
                                            NavigationController.openBot(
                                                profile,
                                                state.sourceConnectionProfileId,
                                                resolution?.sessionId,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                            )
                        }
                    }
                }
        }
    }

    if (showCreateGroup) {
        CreateGroupDialog(
            state = state,
            onDismiss = { showCreateGroup = false },
            onCreate = { title, members ->
                if (groupsViewModel.createRoom(title, members)) {
                    showCreateGroup = false
                }
            },
        )
    }
}

/** Locally resolved session id for a roster bot, or null while unresolved. */
internal fun resolvedSessionIdFor(
    profile: ProfileInfo,
    resolutions: Map<String, BotResolutionState>,
): String? = profile.canonicalSessionId ?: resolutions[profile.name]?.sessionId

@Composable
private fun GroupRoomRow(
    room: BotGroupRoomEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                room.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.bots_group_delete),
                )
            }
        }
    }
}

@Composable
private fun CreateGroupDialog(
    state: BotsUiState,
    onDismiss: () -> Unit,
    onCreate: (title: String, members: List<Pair<String, String?>>) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    val validity = BotGroupPolicy.validate(selected)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bots_group_create_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.bots_group_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.bots_group_member_count, selected.size),
                    style = MaterialTheme.typography.labelSmall,
                )
                state.displayProfiles.forEach { profile ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = profile.name in selected,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    selected.add(profile.name)
                                } else {
                                    selected.remove(profile.name)
                                }
                            },
                        )
                        Text(profile.effectiveTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = validity == BotGroupRosterValidity.OK,
                onClick = {
                    val members =
                        state.displayProfiles
                            .filter { it.name in selected }
                            .map { it.name to resolvedSessionIdFor(it, state.resolutions) }
                    onCreate(title, members)
                },
            ) {
                Text(
                    when (validity) {
                        BotGroupRosterValidity.TOO_FEW -> stringResource(R.string.bots_group_too_few)
                        BotGroupRosterValidity.TOO_MANY -> stringResource(R.string.bots_group_too_many)
                        else -> stringResource(R.string.bots_group_create)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

internal fun canOpenBot(
    profile: ProfileInfo,
    sourceConnectionProfileId: String?,
    selectedConnectionProfileId: String?,
): Boolean =
    profile.canonicalSessionId != null &&
        !sourceConnectionProfileId.isNullOrBlank() &&
        sourceConnectionProfileId == selectedConnectionProfileId

/**
 * A bot is openable through its producing connection profile with either the
 * server canonical session id or a locally resolved session id.
 */
internal fun canOpenBot(
    profile: ProfileInfo,
    sourceConnectionProfileId: String?,
    selectedConnectionProfileId: String?,
    resolvedSessionId: String?,
): Boolean =
    (profile.canonicalSessionId != null || !resolvedSessionId.isNullOrBlank()) &&
        !sourceConnectionProfileId.isNullOrBlank() &&
        sourceConnectionProfileId == selectedConnectionProfileId

@Composable
private fun BotCard(
    profile: ProfileInfo,
    isActive: Boolean,
    unresolved: BotSessionResolution?,
    onClick: (() -> Unit)?,
) {
    Card(
        Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() },
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BotAvatar(
                name = profile.name,
                avatar = profile.botMeta()?.avatar,
                size = 44.dp,
                isActive = isActive,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.effectiveTitle,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (profile.effectiveTitle != profile.name) {
                    Text("@${profile.name}", style = MaterialTheme.typography.labelSmall)
                }
                if (profile.effectiveDescription.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        profile.effectiveDescription,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (unresolved != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(unresolved.uiLabel()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun BotSessionResolution.uiLabel(): Int =
    when (this) {
        is BotSessionResolution.CreateUnsupported -> R.string.bots_session_unprovisioned
        is BotSessionResolution.Ambiguous -> R.string.bots_session_ambiguous
        is BotSessionResolution.Failed -> R.string.bots_session_resolve_failed
        is BotSessionResolution.ProvisionIncomplete -> R.string.bots_session_partial
        is BotSessionResolution.Resolved -> R.string.bots_session_ready
    }

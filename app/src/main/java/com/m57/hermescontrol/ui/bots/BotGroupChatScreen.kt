package com.m57.hermescontrol.ui.bots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.bots.BotGroupRepository
import com.m57.hermescontrol.data.bots.GroupTranscript
import com.m57.hermescontrol.data.bots.GroupTranscriptEntry
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.local.HermesDatabase
import com.m57.hermescontrol.ui.common.ErrorState
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.LoadingState
import com.m57.hermescontrol.ui.common.NavIcon

/**
 * Local phone group chat drilldown: merged transcript over member sessions
 * with a minimal composer. Profile-scoped and durably persisted in Room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotGroupChatScreen(
    roomId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val viewModel: BotGroupChatViewModel =
        viewModel(key = "bot-group-$roomId") {
            BotGroupChatViewModel(
                roomId = roomId,
                repository = BotGroupRepository(HermesDatabase.get(appContext).botGroupDao()),
            )
        }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedProfileId by AuthManager.selectedProfileIdFlow.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Reliable profile-switch fencing: the moment the active connection
    // profile stops owning the room, the room closes for reads and sends.
    LaunchedEffect(selectedProfileId) {
        viewModel.onActiveProfileChanged(selectedProfileId)
    }

    val noticeText =
        when (state.notice) {
            GroupSendNotice.UNRESOLVED_MEMBERS -> stringResource(R.string.bots_group_send_unresolved)
            GroupSendNotice.SEND_FAILED -> stringResource(R.string.bots_group_send_failed)
            GroupSendNotice.PROFILE_CHANGED -> stringResource(R.string.bots_group_profile_changed)
            null -> null
        }

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(noticeText.orEmpty())
            viewModel.clearNotice()
        }
    }

    HermesScaffold(
        modifier = modifier,
        title = {
            Text(
                state.room?.title.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        drawerGesturesEnabled = false,
        navigationIcon = NavIcon.Back(onBack),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when {
            state.room == null && !state.fenced ->
                LoadingState(Modifier.padding(paddingValues))

            state.fenced ->
                ErrorState(
                    stringResource(R.string.bots_group_profile_changed),
                    onRetry = onBack,
                )

            else -> {
                val room = state.room ?: return@HermesScaffold
                GroupRoomContent(
                    state = state,
                    roomProfileId = room.connectionProfileId,
                    onSend = viewModel::send,
                    paddingValues = paddingValues,
                )
            }
        }
    }
}

@Composable
private fun GroupRoomContent(
    state: BotGroupChatUiState,
    roomProfileId: String,
    onSend: (String) -> Unit,
    paddingValues: PaddingValues,
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.bots_group_members_label) +
                        ": " + state.members.joinToString { it.botName },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.messages, key = { it.id }) { message ->
                GroupMessageRow(message)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(stringResource(R.string.bots_group_message_hint)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            IconButton(
                enabled = !state.isSending && draft.isNotBlank(),
                onClick = {
                    onSend(draft)
                    draft = ""
                },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.bots_group_send),
                )
            }
        }
    }
}

@Composable
private fun GroupMessageRow(message: GroupTranscriptEntry) {
    val isUser = message.sender == GroupTranscript.USER_SENDER
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth(0.85f)
                .padding(
                    start = if (isUser) 32.dp else 0.dp,
                ),
        ) {
            Text(
                message.sender,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                message.content,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

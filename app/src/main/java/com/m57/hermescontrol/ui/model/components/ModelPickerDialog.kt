package com.m57.hermescontrol.ui.model.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.m57.hermescontrol.data.model.ModelCapabilities
import com.m57.hermescontrol.data.model.ModelProvider
import com.m57.hermescontrol.data.model.PinnedModel
import com.m57.hermescontrol.ui.common.LoadingState
import com.m57.hermescontrol.ui.common.SearchBar

internal data class ModelPickerSection(
    val provider: ModelProvider,
    val visibleModels: List<String>,
)

internal fun modelPickerSections(
    providers: List<ModelProvider>,
    query: String,
    expandedProvider: String?,
): List<ModelPickerSection> {
    val normalizedQuery = query.trim()
    return providers.mapNotNull { provider ->
        val models = provider.models.orEmpty()
        if (normalizedQuery.isEmpty()) {
            ModelPickerSection(
                provider = provider,
                visibleModels = if (provider.slug == expandedProvider) models else emptyList(),
            )
        } else {
            val providerMatches =
                provider.name.contains(normalizedQuery, ignoreCase = true) ||
                    provider.slug.contains(normalizedQuery, ignoreCase = true)
            val matchingModels =
                if (providerMatches) {
                    models
                } else {
                    models.filter { it.contains(normalizedQuery, ignoreCase = true) }
                }
            matchingModels.takeIf { it.isNotEmpty() }?.let {
                ModelPickerSection(provider = provider, visibleModels = it)
            }
        }
    }
}

/**
 * Reusable model picker used by both the global model screen and the
 * in-session `/model` hot-swap (issue #589). Selecting a provider/model pair
 * invokes [onSelect]; the consumer decides what to do with it (global
 * `config.yaml` assignment vs. a session-scoped `/model` slash command).
 *
 * [pinnedModels] renders a pinned section at the top (mirrors the global model
 * screen's pin section) so frequently-used models are one tap away.
 * [onPinToggle] allows pinning and unpinning models directly inside the dialog.
 */
@Composable
fun ModelPickerDialog(
    providers: List<ModelProvider>,
    title: String,
    isLoading: Boolean = false,
    pinnedModels: List<PinnedModel> = emptyList(),
    selectedModel: String? = null,
    onPinToggle: ((provider: String, model: String) -> Unit)? = null,
    onSelect: (provider: String, model: String) -> Unit,
    onDismiss: () -> Unit,
    imeInsets: WindowInsets = WindowInsets.ime,
) {
    var pickerQuery by remember { mutableStateOf("") }
    var expandedProvider by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier =
            Modifier
                .windowInsetsPadding(imeInsets)
                .testTag("model_picker_dialog"),
        properties = DialogProperties(decorFitsSystemWindows = false),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier =
                        Modifier
                            .size(32.dp)
                            .testTag("model_picker_close"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        text = {
            Column {
                if (isLoading) {
                    LoadingState(
                        subtitle = "Loading models…",
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (providers.isEmpty() && pinnedModels.isEmpty()) {
                    Text(
                        text = "No models available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val filteredPinned =
                        remember(pickerQuery, pinnedModels) {
                            if (pickerQuery.isBlank()) {
                                pinnedModels
                            } else {
                                pinnedModels.filter {
                                    it.modelName.contains(pickerQuery, ignoreCase = true) ||
                                        it.providerSlug.contains(pickerQuery, ignoreCase = true)
                                }
                            }
                        }

                    val pinnedSet =
                        remember(pinnedModels) {
                            pinnedModels.map { "${it.providerSlug}:${it.modelName}" }.toSet()
                        }

                    val providerSections =
                        remember(pickerQuery, providers, expandedProvider) {
                            modelPickerSections(providers, pickerQuery, expandedProvider)
                        }

                    SearchBar(
                        query = pickerQuery,
                        onQueryChange = { pickerQuery = it },
                        modifier = Modifier.testTag("model_picker_search"),
                        placeholder = "Search models and providers...",
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier =
                            Modifier
                                .weight(1f, fill = false)
                                .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // ── Pinned section ──
                        if (filteredPinned.isNotEmpty()) {
                            item(key = "pinned-header") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PushPin,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "Pinned",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            items(
                                filteredPinned,
                                key = { "pinned:${it.providerSlug}:${it.modelName}" },
                            ) { pinned ->
                                ModelItemCard(
                                    modelName = pinned.modelName,
                                    capabilities =
                                        providers
                                            .find {
                                                it.slug == pinned.providerSlug
                                            }?.capabilities
                                            ?.get(pinned.modelName),
                                    isPinned = true,
                                    isSelected =
                                        selectedModel == "${pinned.providerSlug}/${pinned.modelName}",
                                    onPinToggle =
                                        if (onPinToggle != null) {
                                            { onPinToggle(pinned.providerSlug, pinned.modelName) }
                                        } else {
                                            null
                                        },
                                    onClick = { onSelect(pinned.providerSlug, pinned.modelName) },
                                )
                            }
                        }

                        // ── Provider-first inventory ──
                        providerSections.forEach { section ->
                            val provider = section.provider
                            val expanded = pickerQuery.isNotBlank() || expandedProvider == provider.slug
                            item(key = "header:${provider.slug}") {
                                Surface(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp, bottom = 2.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                expandedProvider =
                                                    if (expandedProvider == provider.slug) null else provider.slug
                                            }.testTag("model_picker_provider_${provider.slug}"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border =
                                        BorderStroke(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        ),
                                ) {
                                    Row(
                                        modifier =
                                            Modifier.padding(
                                                start = 12.dp,
                                                end = 4.dp,
                                                top = 8.dp,
                                                bottom = 8.dp,
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = provider.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            text = "${provider.models.orEmpty().size} models",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Icon(
                                            imageVector =
                                                if (expanded) {
                                                    Icons.Default.ExpandLess
                                                } else {
                                                    Icons.Default.ExpandMore
                                                },
                                            contentDescription =
                                                if (expanded) {
                                                    "Collapse ${provider.name} models"
                                                } else {
                                                    "Expand ${provider.name} models"
                                                },
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(32.dp).padding(6.dp),
                                        )
                                    }
                                }
                            }

                            items(
                                items = section.visibleModels,
                                key = { model -> "${provider.slug}:$model" },
                            ) { model ->
                                val isPinned = "${provider.slug}:$model" in pinnedSet
                                ModelItemCard(
                                    modelName = model,
                                    capabilities = provider.capabilities?.get(model),
                                    isPinned = isPinned,
                                    isSelected = selectedModel == "${provider.slug}/$model",
                                    onPinToggle =
                                        if (onPinToggle != null) {
                                            { onPinToggle(provider.slug, model) }
                                        } else {
                                            null
                                        },
                                    onClick = { onSelect(provider.slug, model) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun ModelItemCard(
    modelName: String,
    capabilities: ModelCapabilities?,
    isPinned: Boolean,
    isSelected: Boolean = false,
    onPinToggle: (() -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(12.dp))
                .semantics { selected = isSelected }
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    },
            ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                modelCapabilityHint(capabilities)?.let { hint ->
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onPinToggle != null) {
                IconButton(
                    onClick = onPinToggle,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (isPinned) "Unpin model" else "Pin model",
                        tint =
                            if (isPinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

internal fun modelCapabilityHint(capabilities: ModelCapabilities?): String? =
    when {
        capabilities?.reasoning == false -> "No reasoning"
        capabilities?.can_disable_reasoning == false ->
            "Reasoning always on"

        else -> null
    }

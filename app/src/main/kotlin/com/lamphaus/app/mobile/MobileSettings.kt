package com.lamphaus.app.mobile

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lamphaus.app.R
import com.lamphaus.app.ui.AppUiState
import com.lamphaus.app.ui.AppViewModel
import com.lamphaus.core.model.PairedDevice
import com.lamphaus.core.model.ProfileKind

internal enum class SettingsSection(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    PROFILES(R.string.profiles, Icons.Outlined.Person),
    ADDONS(R.string.addons, Icons.Outlined.Extension),
    PLAYBACK(R.string.playback, Icons.Outlined.PlayCircle),
    PAIRED_DEVICES(R.string.paired_devices, Icons.Outlined.Tv),
    SPOILER_PROTECTION(R.string.spoiler_protection, Icons.Outlined.Visibility),
    ARTWORK(R.string.artwork, Icons.Outlined.Image),
    PRIVACY(R.string.privacy, Icons.Outlined.Lock),
    ACCOUNT(R.string.account, Icons.Outlined.AccountCircle),
}

@Composable
internal fun MobileSettingsScreen(state: AppUiState, viewModel: AppViewModel, onBack: () -> Unit) {
    var section by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    LaunchedEffect(Unit) { viewModel.refreshArtworkKeyStatus() }
    BackHandler(enabled = section != null) { section = null }
    AnimatedContent(
        targetState = section,
        transitionSpec = {
            val target = targetState
            val initial = initialState
            val forward = target != null &&
                (initial == null || target.ordinal > initial.ordinal)
            if (forward) {
                (slideInHorizontally { it / 4 } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally { -it / 4 } + fadeOut(tween(160)))
            } else {
                (slideInHorizontally { -it / 4 } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally { it / 4 } + fadeOut(tween(160)))
            }
        },
        label = "settings-section",
    ) { current ->
        when (current) {
            null -> SettingsRootMenu(onSelect = { section = it })
            SettingsSection.PROFILES -> SettingsProfilesPage(state, viewModel)
            SettingsSection.ADDONS -> SettingsAddonsPage(state, viewModel)
            SettingsSection.PLAYBACK -> SettingsPlaybackPage(state, viewModel)
            SettingsSection.PAIRED_DEVICES -> SettingsPairedDevicesPage(state, viewModel)
            SettingsSection.SPOILER_PROTECTION -> SettingsSpoilerPage(state, viewModel)
            SettingsSection.ARTWORK -> SettingsArtworkPage(state, viewModel)
            SettingsSection.PRIVACY -> SettingsPrivacyPage(state, viewModel)
            SettingsSection.ACCOUNT -> SettingsAccountPage(state, viewModel)
        }
    }
}

@Composable
private fun SettingsRootMenu(onSelect: (SettingsSection) -> Unit) {
    val sections = buildList {
        add(SettingsSection.PROFILES)
        add(SettingsSection.ADDONS)
        add(SettingsSection.PLAYBACK)
        if (com.lamphaus.app.BuildConfig.CLOUD_CONFIGURED) {
            add(SettingsSection.PAIRED_DEVICES)
        }
        add(SettingsSection.SPOILER_PROTECTION)
        add(SettingsSection.ARTWORK)
        add(SettingsSection.PRIVACY)
        if (com.lamphaus.app.BuildConfig.CLOUD_CONFIGURED) {
            add(SettingsSection.ACCOUNT)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        MobileScreenHeader(stringResource(R.string.settings))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MobileTokens.spacingScreen, vertical = 8.dp),
            shape = RoundedCornerShape(MobileTokens.radiusSection),
            color = MobileTokens.surfaceRaised,
        ) {
            SettingsMenuList(sections = sections, onSelect = onSelect)
        }
    }
}

@Composable
private fun SettingsPlaybackPage(state: AppUiState, viewModel: AppViewModel) {
    val playback = state.playbackSettings
    SettingsPage(title = stringResource(R.string.playback)) {
        item {
            SettingsCard(stringResource(R.string.episode_playback)) {
                PlaybackSettingRow(
                    title = stringResource(R.string.skip_intro),
                    description = stringResource(R.string.skip_intro_description),
                    checked = playback.skipIntroEnabled,
                    onCheckedChange = {
                        viewModel.setPlaybackSettings(playback.copy(skipIntroEnabled = it))
                    },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                PlaybackSettingRow(
                    title = stringResource(R.string.skip_ending),
                    description = stringResource(R.string.skip_ending_description),
                    checked = playback.skipEndingEnabled,
                    onCheckedChange = {
                        viewModel.setPlaybackSettings(playback.copy(skipEndingEnabled = it))
                    },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                PlaybackSettingRow(
                    title = stringResource(R.string.next_episode),
                    description = stringResource(R.string.next_episode_description),
                    checked = playback.nextEpisodeEnabled,
                    onCheckedChange = {
                        viewModel.setPlaybackSettings(playback.copy(nextEpisodeEnabled = it))
                    },
                )
            }
        }
    }
}

@Composable
private fun PlaybackSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.semantics(mergeDescendants = true) {},
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
internal fun SettingsMenuList(sections: List<SettingsSection>, onSelect: (SettingsSection) -> Unit) {
    Column {
        sections.forEachIndexed { index, section ->
            if (index > 0) {
                HorizontalDivider(color = MobileTokens.hairline)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) { onSelect(section) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    section.icon,
                    contentDescription = null,
                    tint = MobileTokens.textMuted,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(section.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MobileTokens.textMuted,
                )
            }
        }
    }
}

@Composable
private fun SettingsPage(title: String, content: LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        MobileScreenHeader(title)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MobileTokens.spacingScreen,
                end = MobileTokens.spacingScreen,
                top = 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(MobileTokens.sectionGap),
            content = content,
        )
    }
}

@Composable
private fun SettingsProfilesPage(state: AppUiState, viewModel: AppViewModel) {
    SettingsPage(title = stringResource(R.string.profiles)) {
        item {
            SettingsCard(stringResource(R.string.profiles)) {
                state.profiles.forEachIndexed { index, profile ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                    ListItem(
                        headlineContent = { Text(profile.name) },
                        supportingContent = {
                            Text(stringResource(if (profile.kind == ProfileKind.CHILD) R.string.child_profile else R.string.adult_profile))
                        },
                        leadingContent = { Icon(Icons.Outlined.Person, null) },
                        trailingContent = {
                            TextButton(onClick = { viewModel.selectProfile(profile.id) }, enabled = state.activeProfileId != profile.id) {
                                Text(stringResource(if (state.activeProfileId == profile.id) R.string.active else R.string.switch_profile))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                val kidsName = stringResource(R.string.kids_profile_name)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { viewModel.addProfile(kidsName, true) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, null, tint = MobileTokens.accent)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_child_profile), color = MobileTokens.accent)
                }
            }
        }
    }
}

@Composable
private fun SettingsAddonsPage(state: AppUiState, viewModel: AppViewModel) {
    var providerUrl by rememberSaveable { mutableStateOf("") }
    SettingsPage(title = stringResource(R.string.addons)) {
        item {
            SettingsCard(stringResource(R.string.addons)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = providerUrl,
                        onValueChange = { providerUrl = it },
                        label = { Text(stringResource(R.string.addon_address)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { viewModel.addProvider(providerUrl) },
                        enabled = providerUrl.isNotBlank(),
                    ) { Text(stringResource(R.string.install_addon)) }
                    OutlinedButton(
                        onClick = viewModel::refreshContent,
                        enabled = state.providers.isNotEmpty() && !state.refreshing,
                    ) {
                        Text(stringResource(R.string.refresh_catalogs))
                    }
                }
                state.providers.forEachIndexed { index, provider ->
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                    ListItem(
                        headlineContent = { Text(provider.displayName) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (provider.sortOrder < 0) R.string.included_catalog
                                    else if (provider.enabled) R.string.enabled
                                    else R.string.disabled,
                                ),
                            )
                        },
                        trailingContent = {
                            if (provider.sortOrder >= 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = provider.enabled,
                                        onCheckedChange = { viewModel.toggleProvider(provider.id, it) },
                                    )
                                    IconButton(onClick = { viewModel.removeProvider(provider.id) }) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = stringResource(R.string.remove_provider_format, provider.displayName),
                                        )
                                    }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    if (index < state.providers.lastIndex) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPairedDevicesPage(state: AppUiState, viewModel: AppViewModel) {
    var deviceToRevoke by remember { mutableStateOf<PairedDevice?>(null) }
    LaunchedEffect(Unit) { viewModel.loadDevices() }
    SettingsPage(title = stringResource(R.string.paired_devices)) {
        item {
            SettingsCard(stringResource(R.string.paired_devices)) {
                if (state.pairedDevices.isEmpty()) {
                    Text(
                        stringResource(R.string.no_paired_devices),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.pairedDevices.forEachIndexed { index, device ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                    ListItem(
                        headlineContent = { Text(device.label) },
                        supportingContent = { Text(device.createdAt?.take(10) ?: device.platform) },
                        leadingContent = { Icon(Icons.Outlined.Tv, null) },
                        trailingContent = {
                            TextButton(onClick = {
                                deviceToRevoke = device
                            }) { Text(stringResource(R.string.disconnect)) }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
    deviceToRevoke?.let { device ->
        AlertDialog(
            onDismissRequest = { deviceToRevoke = null },
            title = { Text(stringResource(R.string.disconnect_title)) },
            text = { Text(stringResource(R.string.disconnect_body_format, device.label)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.revokeDevice(device.id, device.label)
                    deviceToRevoke = null
                }) {
                    Text(stringResource(R.string.disconnect), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToRevoke = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SettingsSpoilerPage(state: AppUiState, viewModel: AppViewModel) {
    SettingsPage(title = stringResource(R.string.spoiler_protection)) {
        item {
            SettingsCard(stringResource(R.string.spoiler_protection)) {
                Text(
                    stringResource(R.string.spoiler_protection_description),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.protect_spoilers)) },
                    supportingContent = { Text(stringResource(R.string.spoiler_protection_description)) },
                    leadingContent = { Icon(Icons.Outlined.Visibility, null) },
                    trailingContent = {
                        Switch(
                            checked = state.spoilerProtection.enabled,
                            onCheckedChange = {
                                viewModel.setSpoilerProtection(state.spoilerProtection.copy(enabled = it))
                            },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.blur_episode_artwork)) },
                    supportingContent = { Text(stringResource(R.string.blur_episode_artwork_description)) },
                    trailingContent = {
                        Switch(
                            checked = state.spoilerProtection.blurEpisodeArtwork,
                            enabled = state.spoilerProtection.enabled,
                            onCheckedChange = {
                                viewModel.setSpoilerProtection(state.spoilerProtection.copy(blurEpisodeArtwork = it))
                            },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.blur_episode_synopsis)) },
                    supportingContent = { Text(stringResource(R.string.blur_episode_synopsis_description)) },
                    trailingContent = {
                        Switch(
                            checked = state.spoilerProtection.blurEpisodeSynopsis,
                            enabled = state.spoilerProtection.enabled,
                            onCheckedChange = {
                                viewModel.setSpoilerProtection(state.spoilerProtection.copy(blurEpisodeSynopsis = it))
                            },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun SettingsArtworkPage(state: AppUiState, viewModel: AppViewModel) {
    var artworkKeys by rememberSaveable { mutableStateOf<Map<String, String>>(emptyMap()) }
    var pendingArtworkStorageMode by remember { mutableStateOf<Boolean?>(null) }
    SettingsPage(title = stringResource(R.string.artwork)) {
        item {
            SettingsCard(stringResource(R.string.artwork)) {
                Text(
                    stringResource(R.string.artwork_settings_description),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.local_only_artwork_keys)) },
                    supportingContent = { Text(stringResource(R.string.local_only_artwork_keys_description)) },
                    trailingContent = {
                        Switch(
                            checked = state.localOnlyArtworkKeys,
                            onCheckedChange = { pendingArtworkStorageMode = it },
                            enabled = !state.artworkStorageModeChanging,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                if (state.artworkProviders.isEmpty() && state.artworkProviderCatalogError != null) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.artworkProviderCatalogError, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::refreshArtworkKeyStatus) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
        items(state.artworkProviders, key = { it.provider.value }) { provider ->
            val providerId = provider.provider
            val providerName = provider.displayName
            val apiKey = artworkKeys[providerId.value].orEmpty()
            val failure = state.lastArtworkLookupFailures[providerId]?.let {
                DateUtils.getRelativeTimeSpanString(
                    it,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString()
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MobileTokens.radiusSection),
                colors = CardDefaults.cardColors(containerColor = MobileTokens.surface),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(providerName, style = MaterialTheme.typography.titleLarge)
                    if (!provider.enabled) {
                        Text("This provider is no longer available. You can remove its saved key.")
                        if (provider.configured) {
                            OutlinedButton(
                                onClick = { viewModel.deleteArtworkKey(providerId) },
                                enabled = !state.artworkStorageModeChanging,
                            ) {
                                Text(stringResource(R.string.remove_artwork_key))
                            }
                        }
                    } else {
                        Text(provider.purpose, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(
                            onClick = { viewModel.openArtworkProviderKeyPage(providerId) },
                            modifier = Modifier.sizeIn(minHeight = 48.dp),
                        ) {
                            Text(stringResource(R.string.artwork_get_key, providerName))
                        }
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { value -> artworkKeys = artworkKeys + (providerId.value to value) },
                            label = { Text(stringResource(R.string.artwork_api_key)) },
                            placeholder = { Text(stringResource(R.string.artwork_api_key_placeholder)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            onClick = { viewModel.reportMessage(provider.helpText) },
                            modifier = Modifier.sizeIn(minHeight = 48.dp),
                        ) {
                            Text(stringResource(R.string.artwork_get_help))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    viewModel.saveArtworkKey(providerId, apiKey)
                                    artworkKeys = artworkKeys - providerId.value
                                },
                                enabled = !state.artworkStorageModeChanging && apiKey.isNotBlank(),
                            ) {
                                Text(stringResource(R.string.save_artwork_key))
                            }
                            if (provider.configured) {
                                OutlinedButton(
                                    onClick = { viewModel.deleteArtworkKey(providerId) },
                                    enabled = !state.artworkStorageModeChanging,
                                ) {
                                    Text(stringResource(R.string.remove_artwork_key))
                                }
                            }
                        }
                        Text(
                            when {
                                state.artworkKeyStatusLoading -> stringResource(R.string.artwork_key_loading, providerName)
                                provider.configured -> stringResource(R.string.artwork_key_active, providerName)
                                else -> stringResource(R.string.artwork_key_not_configured, providerName)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        failure?.let {
                            Text(
                                stringResource(R.string.artwork_key_last_lookup_failed, it),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
    pendingArtworkStorageMode?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingArtworkStorageMode = null },
            title = {
                Text(
                    stringResource(
                        if (target) R.string.artwork_storage_enable_title
                        else R.string.artwork_storage_disable_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (target) R.string.artwork_storage_enable_body
                        else R.string.artwork_storage_disable_body,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingArtworkStorageMode = null
                    viewModel.changeArtworkKeyStorageMode(target)
                }) {
                    Text(stringResource(R.string.delete_keys_and_switch), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingArtworkStorageMode = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsPrivacyPage(state: AppUiState, viewModel: AppViewModel) {
    SettingsPage(title = stringResource(R.string.privacy)) {
        item {
            SettingsCard(stringResource(R.string.privacy)) {
                ConsentRow(stringResource(R.string.crash_reports), state.diagnostics.crashReports) {
                    viewModel.setDiagnostics(state.diagnostics.copy(crashReports = it))
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                ConsentRow(stringResource(R.string.performance_metrics), state.diagnostics.performanceMetrics) {
                    viewModel.setDiagnostics(state.diagnostics.copy(performanceMetrics = it))
                }
                Text(
                    stringResource(R.string.diagnostics_explanation),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsAccountPage(state: AppUiState, viewModel: AppViewModel) {
    var deleteAccountOpen by remember { mutableStateOf(false) }
    SettingsPage(title = stringResource(R.string.account)) {
        item {
            SettingsCard(stringResource(R.string.account)) {
                Text(
                    stringResource(R.string.delete_account_explanation),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { deleteAccountOpen = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.delete_account),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    if (deleteAccountOpen) {
        AlertDialog(
            onDismissRequest = { deleteAccountOpen = false },
            title = { Text(stringResource(R.string.delete_account)) },
            text = { Text(stringResource(R.string.delete_account_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteAccountOpen = false
                    viewModel.deleteAccount()
                }) {
                    Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteAccountOpen = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MobileTokens.radiusSection))
                .background(MobileTokens.surfaceRaised),
            content = content,
        )
    }
}

@Composable
private fun ConsentRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked, onChecked) },
        modifier = Modifier.sizeIn(minHeight = 48.dp).semantics(mergeDescendants = true) {},
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

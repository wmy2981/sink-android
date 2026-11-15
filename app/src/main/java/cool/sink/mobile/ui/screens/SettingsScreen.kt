package cool.sink.mobile.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cool.sink.mobile.BuildConfig
import cool.sink.mobile.R
import cool.sink.mobile.data.SettingsStore
import cool.sink.mobile.data.SinkRepository
import cool.sink.mobile.data.ThemeMode
import cool.sink.mobile.ui.components.ConfirmDialog
import cool.sink.mobile.ui.components.SinkLogo
import cool.sink.mobile.ui.openUrl
import cool.sink.mobile.ui.vm.SettingsViewModel

@Composable
fun SettingsScreen(
    repository: SinkRepository,
    settingsStore: SettingsStore,
    onBack: () -> Unit,
    onDisconnected: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(repository, settingsStore)),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val verifying by viewModel.verifying.collectAsStateWithLifecycle()
    val disconnected by viewModel.disconnected.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDisconnectDialog by remember { mutableStateOf(false) }
    val notConnected = stringResource(R.string.settings_not_connected)
    val notVerified = stringResource(R.string.settings_not_verified)

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }
    LaunchedEffect(disconnected) {
        if (disconnected) onDisconnected()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SinkLogo(size = 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = settings.baseUrl.ifBlank { notConnected },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = settings.email.ifBlank { notVerified },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = authMethodLabel(settings.authMethod),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = viewModel::verify,
                            enabled = !verifying,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (verifying) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.action_reverify))
                            }
                        }
                        TextButton(
                            onClick = { showDisconnectDialog = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.action_disconnect), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            SettingsCard(icon = Icons.Outlined.Palette, title = stringResource(R.string.section_appearance)) {
                Text(
                    text = stringResource(R.string.label_theme_mode),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(themeLabel(mode)) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.DarkMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.label_dynamic_color), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                stringResource(R.string.dynamic_color_hint)
                            } else {
                                stringResource(R.string.dynamic_color_unsupported)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                        onCheckedChange = viewModel::setDynamicColor,
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    )
                }
            }

            SettingsCard(icon = Icons.Outlined.Info, title = stringResource(R.string.section_about)) {
                InfoLine(stringResource(R.string.label_version), "v${BuildConfig.VERSION_NAME}")
                LinkRow(
                    icon = Icons.Outlined.Link,
                    label = stringResource(R.string.link_sink_project),
                    onClick = { openUrl(context, "https://github.com/ccbikai/Sink") },
                )
                LinkRow(
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    label = stringResource(R.string.link_sink_docs),
                    onClick = { openUrl(context, "https://sink.cool") },
                )
                Text(
                    text = stringResource(R.string.about_blurb),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = stringResource(R.string.credentials_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }

    if (showDisconnectDialog) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_disconnect_title),
            message = stringResource(R.string.dialog_disconnect_message),
            confirmLabel = stringResource(R.string.action_disconnect),
            onConfirm = {
                showDisconnectDialog = false
                viewModel.disconnect()
            },
            onDismiss = { showDisconnectDialog = false },
        )
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun authMethodLabel(method: String): String = when (method) {
    "site-token" -> stringResource(R.string.auth_site_token)
    "access-user" -> stringResource(R.string.auth_access_user)
    "access-service" -> stringResource(R.string.auth_access_service)
    "" -> stringResource(R.string.auth_unverified)
    else -> method
}

package cool.sink.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cool.sink.mobile.R
import cool.sink.mobile.data.SinkRepository
import cool.sink.mobile.ui.asString
import cool.sink.mobile.ui.components.EmptyState
import cool.sink.mobile.ui.vm.LinkEditViewModel
import cool.sink.mobile.util.endOfDayEpochSeconds
import cool.sink.mobile.util.formatDate
import androidx.compose.material.icons.outlined.ErrorOutline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkEditScreen(
    repository: SinkRepository,
    slug: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: LinkEditViewModel = viewModel(factory = LinkEditViewModel.factory(repository, slug)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    fun submitTag() {
        if (tagInput.isNotBlank()) {
            viewModel.addTag(tagInput)
            tagInput = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) stringResource(R.string.edit_title) else stringResource(R.string.create_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(onClick = viewModel::save, enabled = !state.loading) {
                            Text(stringResource(R.string.action_save), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.loading -> EmptyState(
                icon = Icons.Outlined.Link,
                title = stringResource(R.string.edit_loading_title),
                subtitle = stringResource(R.string.edit_loading_subtitle),
                modifier = Modifier.padding(innerPadding),
            )

            state.error != null && !state.isEdit -> EmptyState(
                icon = Icons.Outlined.ErrorOutline,
                title = stringResource(R.string.edit_open_failed),
                subtitle = state.error?.asString().orEmpty(),
                modifier = Modifier.padding(innerPadding),
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack,
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = viewModel::onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_url)) },
                    isError = state.urlError != null,
                    supportingText = {
                        Text(state.urlError?.asString() ?: stringResource(R.string.field_url_hint))
                    },
                    minLines = 2,
                    maxLines = 4,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )

                OutlinedTextField(
                    value = state.slug,
                    onValueChange = viewModel::onSlugChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_slug)) },
                    readOnly = state.isEdit,
                    isError = state.slugError != null,
                    supportingText = {
                        Text(
                            when {
                                state.slugError != null -> state.slugError?.asString().orEmpty()
                                state.isEdit -> stringResource(R.string.slug_locked)
                                else -> stringResource(R.string.slug_hint)
                            },
                        )
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                )

                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.field_expiration),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = state.expiration?.let { stringResource(R.string.expires_at, formatDate(it) ?: "-") } ?: stringResource(R.string.expiration_none),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        if (state.expiration != null) {
                            IconButton(onClick = { viewModel.onExpirationChange(null) }) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cd_clear_expiration))
                            }
                        } else {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                        }
                    }
                }

                OutlinedTextField(
                    value = state.comment,
                    onValueChange = viewModel::onCommentChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_comment)) },
                    minLines = 2,
                    maxLines = 4,
                    shape = MaterialTheme.shapes.large,
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.section_tags), style = MaterialTheme.typography.titleSmall)

                    if (state.tags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.tags.forEach { tag ->
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.removeTag(tag) },
                                    label = { Text(tag) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = tagInput,
                            onValueChange = { tagInput = it },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.field_add_tag)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitTag() }),
                            supportingText = { Text(stringResource(R.string.tag_limit_hint)) },
                        )
                        IconButton(onClick = { submitTag() }) {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.field_add_tag))
                        }
                    }

                    val suggestions = state.knownTags.filterNot { state.tags.contains(it) }
                    if (suggestions.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.section_known_tags),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            suggestions.forEach { tag ->
                                FilterChip(
                                    selected = false,
                                    onClick = { viewModel.toggleTag(tag) },
                                    label = { Text(tag) },
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.advanced_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (state.error != null) {
                    Text(
                        text = state.error?.asString().orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(Modifier.size(24.dp))
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.expiration?.times(1000))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.onExpirationChange(endOfDayEpochSeconds(millis))
                        }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

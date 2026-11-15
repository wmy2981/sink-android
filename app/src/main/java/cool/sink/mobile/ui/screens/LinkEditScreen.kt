package cool.sink.mobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cool.sink.mobile.R
import cool.sink.mobile.data.SinkRepository
import cool.sink.mobile.ui.asString
import cool.sink.mobile.ui.components.CountryPickerDialog
import cool.sink.mobile.ui.components.EditorSection
import cool.sink.mobile.ui.components.EmptyState
import cool.sink.mobile.ui.components.RemoteImage
import cool.sink.mobile.ui.components.SwitchRow
import cool.sink.mobile.ui.components.countryLabel
import cool.sink.mobile.ui.vm.LinkEditViewModel
import cool.sink.mobile.util.applyUtmValues
import cool.sink.mobile.util.datePickerMillis
import cool.sink.mobile.util.endOfDayEpochSeconds
import cool.sink.mobile.util.formatDate
import cool.sink.mobile.util.isValidHttpUrl
import cool.sink.mobile.util.parseUtmValues
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LinkEditScreen(
    repository: SinkRepository,
    slug: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: LinkEditViewModel = viewModel(factory = LinkEditViewModel.factory(repository, slug)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showUtmDialog by rememberSaveable { mutableStateOf(false) }
    var tagInput by rememberSaveable { mutableStateOf("") }
    var linkSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var socialExpanded by rememberSaveable { mutableStateOf(false) }
    var deviceExpanded by rememberSaveable { mutableStateOf(false) }
    var geoExpanded by rememberSaveable { mutableStateOf(false) }
    var countryPickerIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    LaunchedEffect(state.loading) {
        if (!state.loading) {
            // Open the sections that already hold values, like the web editor does.
            linkSettingsExpanded = state.hasStoredPassword || state.cloaking ||
                state.redirectWithQuery || state.proxy || state.unsafe
            socialExpanded = state.title.isNotBlank() || state.description.isNotBlank() || state.image.isNotBlank()
            deviceExpanded = state.google.isNotBlank() || state.apple.isNotBlank()
            geoExpanded = state.geo.isNotEmpty()
        }
    }
    LaunchedEffect(state.appleError, state.googleError) {
        if (state.appleError != null || state.googleError != null) deviceExpanded = true
    }
    LaunchedEffect(state.geoError) {
        if (state.geoError != null) geoExpanded = true
    }
    LaunchedEffect(state.transientError) {
        state.transientError?.let { error ->
            snackbarHostState.showSnackbar(error.resolve(context))
            viewModel.consumeTransientError()
        }
    }
    LaunchedEffect(state.error, state.loadFailed) {
        val error = state.error
        if (error != null && !state.loadFailed) {
            snackbarHostState.showSnackbar(error.resolve(context))
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream -> readAtMost(stream) }
                    }.getOrNull()
                }
                if (bytes == null || bytes.isEmpty()) {
                    viewModel.reportImageReadFailed()
                } else {
                    val mimeType = context.contentResolver.getType(uri)
                        ?.let { if (it == "image/jpg") "image/jpeg" else it }
                    viewModel.uploadImage(bytes, mimeType)
                }
            }
        }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            state.loading -> EmptyState(
                icon = Icons.Outlined.Link,
                title = stringResource(R.string.edit_loading_title),
                subtitle = stringResource(R.string.edit_loading_subtitle),
                modifier = Modifier.padding(innerPadding),
            )

            state.loadFailed -> EmptyState(
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
                    trailingIcon = {
                        IconButton(onClick = { showUtmDialog = true }) {
                            Text(stringResource(R.string.utm_short), style = MaterialTheme.typography.labelMedium)
                        }
                    },
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
                    trailingIcon = if (state.isEdit) {
                        null
                    } else {
                        {
                            Row {
                                IconButton(onClick = viewModel::generateRandomSlug) {
                                    Icon(
                                        Icons.Outlined.Shuffle,
                                        contentDescription = stringResource(R.string.action_random_slug),
                                    )
                                }
                                IconButton(
                                    onClick = viewModel::generateSlugWithAi,
                                    enabled = !state.generatingSlug,
                                ) {
                                    if (state.generatingSlug) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Outlined.AutoAwesome,
                                            contentDescription = stringResource(R.string.action_generate_slug),
                                        )
                                    }
                                }
                            }
                        }
                    },
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

                EditorSection(
                    title = stringResource(R.string.section_link_settings),
                    subtitle = stringResource(R.string.section_link_settings_hint),
                    expanded = linkSettingsExpanded,
                    onToggle = { linkSettingsExpanded = !linkSettingsExpanded },
                ) {
                    SwitchRow(
                        title = stringResource(R.string.field_redirect_with_query),
                        description = stringResource(R.string.field_redirect_with_query_hint),
                        checked = state.redirectWithQuery,
                        onCheckedChange = viewModel::onRedirectWithQueryChange,
                    )
                    SwitchRow(
                        title = stringResource(R.string.field_cloaking),
                        description = stringResource(R.string.field_cloaking_hint),
                        checked = state.cloaking,
                        onCheckedChange = viewModel::onCloakingChange,
                    )
                    SwitchRow(
                        title = stringResource(R.string.field_proxy),
                        description = stringResource(R.string.field_proxy_hint),
                        checked = state.proxy,
                        onCheckedChange = viewModel::onProxyChange,
                    )
                    SwitchRow(
                        title = stringResource(R.string.field_unsafe),
                        description = stringResource(R.string.field_unsafe_hint),
                        checked = state.unsafe,
                        onCheckedChange = viewModel::onUnsafeChange,
                    )
                    OutlinedTextField(
                        value = state.passwordInput.orEmpty(),
                        onValueChange = viewModel::onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_password)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        supportingText = {
                            Text(
                                when {
                                    state.hasStoredPassword && state.passwordInput == null ->
                                        stringResource(R.string.password_stored_hint)
                                    state.hasStoredPassword && state.passwordInput == "" ->
                                        stringResource(R.string.password_clear_hint)
                                    else -> stringResource(R.string.field_password_hint)
                                },
                            )
                        },
                        trailingIcon = {
                            if (state.hasStoredPassword && state.passwordInput == null) {
                                TextButton(onClick = viewModel::clearPassword) {
                                    Text(stringResource(R.string.action_clear))
                                }
                            }
                        },
                    )
                }

                EditorSection(
                    title = stringResource(R.string.section_social),
                    subtitle = stringResource(R.string.section_social_hint),
                    expanded = socialExpanded,
                    onToggle = { socialExpanded = !socialExpanded },
                ) {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_title)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        supportingText = { Text(stringResource(R.string.field_title_hint)) },
                        trailingIcon = {
                            IconButton(
                                onClick = viewModel::generateSocialPreviewWithAi,
                                enabled = !state.generatingOg,
                            ) {
                                if (state.generatingOg) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.AutoAwesome,
                                        contentDescription = stringResource(R.string.action_generate_social),
                                    )
                                }
                            }
                        },
                    )
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = viewModel::onDescriptionChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_description)) },
                        minLines = 2,
                        maxLines = 4,
                        shape = MaterialTheme.shapes.large,
                        supportingText = { Text(stringResource(R.string.field_description_hint)) },
                    )

                    val canUpload = state.slug.isNotBlank() && !state.uploadingImage
                    Text(stringResource(R.string.field_image), style = MaterialTheme.typography.titleSmall)
                    if (state.imagePreviewUrl != null) {
                        Box {
                            RemoteImage(
                                url = state.imagePreviewUrl.orEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(MaterialTheme.shapes.medium),
                                contentDescription = stringResource(R.string.field_image),
                            )
                            if (state.uploadingImage) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                enabled = canUpload,
                            ) { Text(stringResource(R.string.image_replace)) }
                            TextButton(onClick = viewModel::clearImage) {
                                Text(stringResource(R.string.image_remove))
                            }
                        }
                    } else {
                        OutlinedCard(
                            onClick = {
                                if (canUpload) {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                }
                            },
                            enabled = canUpload,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (state.uploadingImage) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                                }
                                Text(
                                    text = stringResource(
                                        when {
                                            state.uploadingImage -> R.string.image_uploading
                                            canUpload -> R.string.image_upload
                                            else -> R.string.error_image_slug_required
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = stringResource(R.string.image_upload_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                EditorSection(
                    title = stringResource(R.string.section_device),
                    subtitle = stringResource(R.string.section_device_hint),
                    expanded = deviceExpanded,
                    onToggle = { deviceExpanded = !deviceExpanded },
                ) {
                    OutlinedTextField(
                        value = state.google,
                        onValueChange = viewModel::onGoogleChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_google)) },
                        placeholder = { Text("https://play.google.com/store/apps/…") },
                        singleLine = true,
                        isError = state.googleError != null,
                        supportingText = { Text(state.googleError?.asString() ?: "") },
                        shape = MaterialTheme.shapes.large,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = state.apple,
                        onValueChange = viewModel::onAppleChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_apple)) },
                        placeholder = { Text("https://apps.apple.com/app/…") },
                        singleLine = true,
                        isError = state.appleError != null,
                        supportingText = { Text(state.appleError?.asString() ?: "") },
                        shape = MaterialTheme.shapes.large,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    )
                }

                EditorSection(
                    title = stringResource(R.string.section_geo),
                    subtitle = stringResource(R.string.section_geo_hint),
                    expanded = geoExpanded,
                    onToggle = { geoExpanded = !geoExpanded },
                ) {
                    state.geo.forEachIndexed { index, rule ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(
                                        onClick = { countryPickerIndex = index },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            text = if (rule.country.isBlank()) {
                                                stringResource(R.string.geo_pick_country)
                                            } else {
                                                countryLabel(rule.country)
                                            },
                                        )
                                    }
                                    IconButton(onClick = { viewModel.removeGeoRule(index) }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_remove))
                                    }
                                }
                                OutlinedTextField(
                                    value = rule.url,
                                    onValueChange = { viewModel.updateGeoUrl(index, it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(R.string.field_url)) },
                                    placeholder = { Text("https://…") },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                                )
                            }
                        }
                    }
                    if (state.geoError != null) {
                        Text(
                            text = state.geoError?.asString().orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::addGeoRule,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.geo_add_rule))
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
                            text = stringResource(R.string.editor_note),
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
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = datePickerMillis(state.expiration))
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

    if (showUtmDialog) {
        UtmDialog(
            initialUrl = state.url,
            onApply = viewModel::onUrlChange,
            onDismiss = { showUtmDialog = false },
        )
    }

    countryPickerIndex?.let { index ->
        CountryPickerDialog(
            selected = state.geo.getOrNull(index)?.country.orEmpty(),
            onSelect = { country ->
                viewModel.updateGeoCountry(index, country)
                countryPickerIndex = null
            },
            onDismiss = { countryPickerIndex = null },
        )
    }
}

@Composable
private fun UtmDialog(
    initialUrl: String,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var values by remember { mutableStateOf(parseUtmValues(initialUrl)) }
    val appliedUrl = applyUtmValues(initialUrl, values)
    val canApply = isValidHttpUrl(appliedUrl)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.utm_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                UtmField(stringResource(R.string.utm_source), values.source, "newsletter") {
                    values = values.copy(source = it)
                }
                UtmField(stringResource(R.string.utm_medium), values.medium, "email") {
                    values = values.copy(medium = it)
                }
                UtmField(stringResource(R.string.utm_campaign), values.campaign, "spring_sale") {
                    values = values.copy(campaign = it)
                }
                UtmField(stringResource(R.string.utm_term), values.term, "running-shoes") {
                    values = values.copy(term = it)
                }
                UtmField(stringResource(R.string.utm_content), values.content, "hero_button") {
                    values = values.copy(content = it)
                }
                Text(
                    text = stringResource(R.string.utm_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(appliedUrl)
                    onDismiss()
                },
                enabled = canApply,
            ) { Text(stringResource(R.string.utm_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun UtmField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
    )
}

/** Reads a little past the 5 MB limit so the view model can report an oversized image. */
private fun readAtMost(stream: InputStream): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = stream.read(buffer)
        if (read < 0) break
        total += read
        output.write(buffer, 0, read)
        if (total > cool.sink.mobile.util.MAX_IMAGE_BYTES) break
    }
    return output.toByteArray()
}

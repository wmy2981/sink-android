package cool.sink.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cool.sink.mobile.R
import cool.sink.mobile.data.Link
import cool.sink.mobile.data.SearchItem
import cool.sink.mobile.data.SinkRepository
import cool.sink.mobile.ui.asString
import cool.sink.mobile.ui.buildShortUrl
import cool.sink.mobile.ui.components.ConfirmDialog
import cool.sink.mobile.ui.components.EmptyState
import cool.sink.mobile.ui.components.InfoRow
import cool.sink.mobile.ui.components.QrCodeDialog
import cool.sink.mobile.ui.components.TagPill
import cool.sink.mobile.ui.copyToClipboard
import cool.sink.mobile.ui.openUrl
import cool.sink.mobile.ui.shareText
import cool.sink.mobile.ui.vm.LinksViewModel
import cool.sink.mobile.ui.vm.SortOption
import cool.sink.mobile.ui.vm.StatusFilter
import cool.sink.mobile.util.decodeUrlForDisplay
import cool.sink.mobile.util.formatDateTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinksScreen(
    repository: SinkRepository,
    refreshSignal: Boolean,
    onRefreshHandled: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: LinksViewModel = viewModel(factory = LinksViewModel.factory(repository)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var selectedLink by remember { mutableStateOf<Link?>(null) }
    var pendingDelete by remember { mutableStateOf<Link?>(null) }
    var qrUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message.resolve(context)) }
    }
    LaunchedEffect(refreshSignal) {
        if (refreshSignal) {
            onRefreshHandled()
            viewModel.refresh(showIndicator = false)
        }
    }

    fun copyLink(link: Link) {
        copyToClipboard(context, context.getString(R.string.clip_label_short_link), buildShortUrl(state.baseUrl, link.slug))
        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.snackbar_copied)) }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.links_title)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_new)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cd_clear_search))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChipDropdown(
                    label = stringResource(state.sort.labelRes),
                    icon = Icons.AutoMirrored.Outlined.Sort,
                    options = SortOption.entries.map { it to stringResource(it.labelRes) },
                    selectedOption = state.sort,
                    onSelect = viewModel::onSortChange,
                )
                ChipDropdown(
                    label = stringResource(state.status.labelRes),
                    icon = Icons.Outlined.FilterList,
                    options = StatusFilter.entries.map { it to stringResource(it.labelRes) },
                    selectedOption = state.status,
                    onSelect = viewModel::onStatusChange,
                )
                if (state.tags.isNotEmpty()) {
                    val tagOptions = listOf<Pair<String?, String>>(null to stringResource(R.string.filter_all_tags)) +
                        state.tags.map { it.name to "${it.name} · ${it.count}" }
                    ChipDropdown(
                        label = state.selectedTag ?: stringResource(R.string.filter_tag),
                        icon = Icons.AutoMirrored.Outlined.Label,
                        options = tagOptions,
                        selectedOption = state.selectedTag,
                        onSelect = viewModel::onTagChange,
                    )
                }
            }

            if (state.searching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    state.error != null && state.links.isEmpty() && state.searchResults == null -> EmptyState(
                        icon = Icons.Outlined.CloudOff,
                        title = stringResource(R.string.state_load_failed),
                        subtitle = state.error?.asString().orEmpty(),
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = { viewModel.refresh() },
                    )

                    state.searchResults != null -> {
                        val results = state.searchResults.orEmpty()
                        if (results.isEmpty() && !state.searching) {
                            EmptyState(
                                icon = Icons.Outlined.Search,
                                title = stringResource(R.string.state_no_results),
                                subtitle = stringResource(R.string.state_no_results_hint),
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 4.dp,
                                    bottom = 100.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(results, key = { it.slug }) { item ->
                                    SearchResultRow(
                                        item = item,
                                        host = state.host,
                                        onClick = { onEdit(item.slug) },
                                    )
                                }
                            }
                        }
                    }

                    state.links.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.LinkOff,
                        title = stringResource(R.string.state_no_links),
                        subtitle = stringResource(R.string.state_no_links_hint),
                        actionLabel = stringResource(R.string.action_new_link),
                        onAction = onCreate,
                    )

                    else -> PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = { viewModel.refresh(showIndicator = false, fromPull = true) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 100.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.links, key = { it.slug }) { link ->
                                LinkCard(
                                    link = link,
                                    host = state.host,
                                    onOpen = { selectedLink = link },
                                    onCopy = { copyLink(link) },
                                    onShare = {
                                        shareText(context, buildShortUrl(state.baseUrl, link.slug))
                                    },
                                    onBrowser = {
                                        if (!openUrl(context, buildShortUrl(state.baseUrl, link.slug))) {
                                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.snackbar_no_browser)) }
                                        }
                                    },
                                    onEdit = { onEdit(link.slug) },
                                    onDelete = { pendingDelete = link },
                                )
                            }
                            if (!state.listComplete) {
                                item(key = "load-more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                    LaunchedEffect(state.links.size) { viewModel.loadMore() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedLink?.let { link ->
        val shortUrl = buildShortUrl(state.baseUrl, link.slug)
        LinkDetailSheet(
            link = link,
            host = state.host,
            shortUrl = shortUrl,
            onDismiss = { selectedLink = null },
            onCopy = { copyLink(link) },
            onOpen = { if (!openUrl(context, shortUrl)) scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.snackbar_no_browser)) } },
            onShare = { shareText(context, shortUrl) },
            onQrCode = { qrUrl = shortUrl },
            onEdit = {
                selectedLink = null
                onEdit(link.slug)
            },
            onDelete = {
                selectedLink = null
                pendingDelete = link
            },
        )
    }

    pendingDelete?.let { link ->
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_title),
            message = stringResource(R.string.dialog_delete_message, link.slug),
            onConfirm = {
                viewModel.delete(link)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    qrUrl?.let { url ->
        QrCodeDialog(text = url, onDismiss = { qrUrl = null })
    }
}

@Composable
private fun ChipDropdown(
    label: String,
    icon: ImageVector,
    options: List<Pair<String?, String>>,
    selectedOption: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            label = { Text(label) },
            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                    leadingIcon = {
                        if (value == selectedOption) {
                            Icon(Icons.Outlined.Check, contentDescription = null)
                        } else {
                            Spacer(Modifier.width(24.dp))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ChipDropdown(
    label: String,
    icon: ImageVector,
    options: List<Pair<SortOption, String>>,
    selectedOption: SortOption,
    onSelect: (SortOption) -> Unit,
) = ChipDropdown(
    label = label,
    icon = icon,
    options = options.map { it.first.apiValue to it.second },
    selectedOption = selectedOption.apiValue,
    onSelect = { value -> options.firstOrNull { it.first.apiValue == value }?.let { onSelect(it.first) } },
)

@Composable
private fun ChipDropdown(
    label: String,
    icon: ImageVector,
    options: List<Pair<StatusFilter, String>>,
    selectedOption: StatusFilter,
    onSelect: (StatusFilter) -> Unit,
) = ChipDropdown(
    label = label,
    icon = icon,
    options = options.map { it.first.apiValue to it.second },
    selectedOption = selectedOption.apiValue,
    onSelect = { value -> options.firstOrNull { it.first.apiValue == value }?.let { onSelect(it.first) } },
)

@Composable
private fun LinkCard(
    link: Link,
    host: String,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onBrowser: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 6.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Normal,
                                fontSize = 13.sp,
                            ),
                        ) { append("$host/") }
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        ) { append(link.slug) }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (link.isExpired) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.status_expired),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.cd_more_actions))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_copy_link)) },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onCopy()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_open_browser)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onBrowser()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = decodeUrlForDisplay(link.url),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 10.dp),
            )

            if (!link.comment.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = link.comment,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 10.dp),
                )
            }

            if (link.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(end = 10.dp),
                ) {
                    link.tags.take(4).forEach { tag -> TagPill(tag) }
                    if (link.tags.size > 4) {
                        Text(
                            text = "+${link.tags.size - 4}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }

            if (link.expiration != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.expires_at, formatDateTime(link.expiration) ?: "-"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(item: SearchItem, host: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "$host/${item.slug}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = decodeUrlForDisplay(item.url),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkDetailSheet(
    link: Link,
    host: String,
    shortUrl: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onQrCode: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = link.slug,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = host,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (link.isExpired) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.status_expired),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            Text(
                text = decodeUrlForDisplay(link.url),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            if (!link.comment.isNullOrBlank()) {
                Text(
                    text = link.comment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (link.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    link.tags.forEach { TagPill(it) }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            InfoRow(stringResource(R.string.info_created), formatDateTime(link.createdAt) ?: "-")
            InfoRow(stringResource(R.string.info_updated), formatDateTime(link.updatedAt) ?: "-")
            if (link.expiration != null) {
                InfoRow(stringResource(R.string.info_expires), formatDateTime(link.expiration) ?: "-")
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_copy))
                }
                OutlinedButton(
                    onClick = onQrCode,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Outlined.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_code_title))
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_share))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_open))
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_edit))
                }
                TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                text = shortUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

package cool.sink.mobile.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cool.sink.mobile.R
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Collapsible card used for the optional groups of the link editor. */
@Composable
fun EditorSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

private sealed interface ImageLoad {
    data object Loading : ImageLoad
    data object Failed : ImageLoad
    data class Success(val bitmap: ImageBitmap) : ImageLoad
}

/**
 * Small network image loader for OpenGraph previews. The instance serves uploaded images from
 * the public `/_assets` route, so no Authorization header is needed.
 */
@Composable
fun RemoteImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val result by produceState<ImageLoad>(ImageLoad.Loading, url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    instanceFollowRedirects = true
                }
                connection.inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()?.let { ImageLoad.Success(it) } ?: ImageLoad.Failed
        }
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = result) {
            is ImageLoad.Success -> Image(
                bitmap = current.bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            ImageLoad.Failed -> Icon(
                imageVector = Icons.Outlined.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ImageLoad.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

/** Two-letter country code to a flag emoji; falls back to a neutral flag. */
fun countryFlag(code: String): String {
    if (code.length != 2 || !code.all { it.isLetter() }) return "🏳️"
    val base = 0x1F1E6
    val first = Character.toChars(base + (code[0].uppercaseChar() - 'A'))
    val second = Character.toChars(base + (code[1].uppercaseChar() - 'A'))
    return String(first) + String(second)
}

fun countryDisplayName(code: String, locale: Locale): String {
    if (code.length != 2) return ""
    return Locale.Builder().setRegion(code.uppercase(Locale.ROOT)).build().getDisplayCountry(locale)
}

/** Searchable ISO country picker used by the geo-routing editor. */
@Composable
fun CountryPickerDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val countries = remember(locale) {
        Locale.getISOCountries()
            .map { code -> code to countryDisplayName(code, locale) }
            .filter { it.second.isNotBlank() }
            .sortedBy { it.second.lowercase(locale) }
    }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, countries) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            countries
        } else {
            countries.filter {
                it.first.equals(needle, ignoreCase = true) ||
                    it.second.contains(needle, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.geo_pick_country)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.geo_search_country)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(R.string.geo_no_country),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        items(filtered, key = { it.first }) { (code, name) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(code) }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${countryFlag(code)}  $name", modifier = Modifier.weight(1f))
                                if (code.equals(selected, ignoreCase = true)) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Compact label for a stored country code, e.g. "🇯🇵 Japan". */
@Composable
fun countryLabel(code: String): String {
    val locale = LocalConfiguration.current.locales[0]
    val name = countryDisplayName(code, locale)
    return if (name.isBlank()) code else "${countryFlag(code)}  $name"
}

package cool.sink.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cool.sink.mobile.util.UiText

@Composable
fun UiText.asString(): String = stringResource(id, *args.toTypedArray())

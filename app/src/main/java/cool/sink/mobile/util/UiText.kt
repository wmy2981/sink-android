package cool.sink.mobile.util

import android.content.Context
import androidx.annotation.StringRes

/** A user-visible string resolved against the current configuration at render time. */
data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList()) {
    fun resolve(context: Context): String = context.getString(id, *args.toTypedArray())
}

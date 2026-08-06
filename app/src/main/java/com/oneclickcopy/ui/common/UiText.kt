package com.oneclickcopy.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Text that a ViewModel can emit without depending on Android resources.
 *
 * ViewModels must not build user-facing strings themselves: hardcoded English
 * cannot be localized and cannot be asserted on in tests without a Context.
 * They describe *what* to say with a resource id; the UI layer resolves it.
 */
sealed interface UiText {

    data class Res(
        @StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    /** For text that genuinely originates outside the app, e.g. a server message. */
    data class Dynamic(val value: String) : UiText

    companion object {
        fun res(@StringRes id: Int, vararg args: Any) = Res(id, args.toList())
    }
}

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Res -> if (args.isEmpty()) {
        stringResource(id)
    } else {
        stringResource(id, *args.toTypedArray())
    }
    is UiText.Dynamic -> value
}

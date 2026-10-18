package com.cyclecare.core.designsystem.text

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * Unresolved, resource-backed user-facing text.
 *
 * ViewModels and pure helpers return this instead of a finished English
 * sentence; the composable that renders it resolves it against the
 * current Android configuration. That keeps business logic free of
 * Compose, keeps rendered text correct after a locale change instead of
 * going stale, and lets unit tests assert on resource identity rather
 * than on English.
 *
 * There is deliberately no `Composed` variant. Assembling a sentence
 * from separately translated fragments is the failure mode this type
 * exists to remove; sentence shape belongs in the resource, with the
 * variable parts passed as [args].
 *
 * [Raw] exists for text that genuinely cannot be a resource: machine
 * formats, ISO dates, and numeric ratios. Each use needs a written
 * justification, because it is the escape hatch that would otherwise
 * quietly undo the whole mechanism.
 */
sealed interface UiText {

    /** A string resource, with optional ordered format arguments. */
    data class Resource(
        @param:StringRes val resId: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    /** A plural resource selected by [quantity]. */
    data class Plural(
        @param:PluralsRes val resId: Int,
        val quantity: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    /**
     * Text that is already final, or cannot be a resource.
     *
     * Justify every use. Legitimate: numeric ratios, ISO-8601 dates and
     * other machine formats the app parses back, values owned by
     * another system. Illegitimate: an English sentence that should
     * have been a resource.
     */
    data class Raw(val value: String) : UiText
}

/**
 * Resolves against the current configuration.
 *
 * Read-only so callers cannot accidentally trigger recomposition
 * through it.
 */
@Composable
@ReadOnlyComposable
fun UiText.resolve(): String = when (this) {
    is UiText.Resource ->
        if (args.isEmpty()) stringResource(resId) else stringResource(resId, *args.toTypedArray())

    is UiText.Plural ->
        if (args.isEmpty()) {
            pluralStringResource(resId, quantity)
        } else {
            pluralStringResource(resId, quantity, *args.toTypedArray())
        }

    is UiText.Raw -> value
}

/**
 * Resolves against an explicit [Context], for code that cannot be
 * composable: notification builders, platform dialogs, adapters.
 */
fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Resource ->
        if (args.isEmpty()) {
            context.getString(resId)
        } else {
            context.getString(resId, *args.toTypedArray())
        }

    is UiText.Plural ->
        context.resources.getQuantityString(resId, quantity, *args.toTypedArray())

    is UiText.Raw -> value
}

/** Shorthand for a resource with no arguments. */
fun @receiver:StringRes Int.asText(): UiText = UiText.Resource(this)

/** Shorthand for a resource with ordered arguments. */
fun uiText(@StringRes resId: Int, vararg args: Any): UiText =
    UiText.Resource(resId, args.toList())

/** Shorthand for a plural resource. */
fun pluralUiText(@PluralsRes resId: Int, quantity: Int, vararg args: Any): UiText =
    UiText.Plural(resId, quantity, args.toList())
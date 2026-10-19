package com.cyclecare.core.domain.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether the user has an unsaved draft anywhere in the app.
 *
 * Spec 15 §11.3 condition 4: "No unsaved draft anywhere in the app."
 *
 * The logging editor is the only draft surface in the product. It lives in a
 * NavHost-scoped ViewModel, so its state is not reachable from the shell
 * that decides whether to show an interstitial. This holder is the shared
 * channel between the two, and it is held on the application container so
 * that it survives the ViewModel being recreated.
 */
interface DraftTracker {

    /** Whether an unsaved draft currently exists. */
    val hasUnsavedDraft: StateFlow<Boolean>

    /**
     * Reports whether the draft surface currently holds unsaved changes.
     *
     * Called by the draft owner whenever its editor state changes, including
     * when the changes are saved and it is no longer a draft.
     */
    fun setUnsavedDraft(present: Boolean)
}

/**
 * In-memory draft tracker.
 *
 * A draft is transient by definition: it is unsaved editor state in the
 * current process. It is deliberately not persisted, because a draft that
 * has been abandoned should not suppress an interstitial forever.
 */
class InMemoryDraftTracker : DraftTracker {

    private val _hasUnsavedDraft = MutableStateFlow(false)
    override val hasUnsavedDraft: StateFlow<Boolean> = _hasUnsavedDraft.asStateFlow()

    override fun setUnsavedDraft(present: Boolean) {
        _hasUnsavedDraft.value = present
    }
}

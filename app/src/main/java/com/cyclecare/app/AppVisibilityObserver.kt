package com.cyclecare.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface AppVisibilityObserver {
    val isForeground: StateFlow<Boolean>
}

class ProcessAppVisibilityObserver(
    /**
     * Invoked each time the app enters the foreground.
     *
     * Spec 15 §11.3 condition 2 counts sessions, and a session is one
     * foreground period. The counter has to be persisted, so the observer
     * cannot own it; it reports the transition and the caller decides what
     * to record.
     */
    private val onEnteredForeground: (suspend () -> Unit)? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AppVisibilityObserver, DefaultLifecycleObserver {
    private val foregroundState = MutableStateFlow(false)

    override val isForeground: StateFlow<Boolean> = foregroundState.asStateFlow()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        foregroundState.value = true
        onEnteredForeground?.let { callback ->
            scope.launch { callback() }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        foregroundState.value = false
    }
}

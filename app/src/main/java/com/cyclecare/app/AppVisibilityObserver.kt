package com.cyclecare.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AppVisibilityObserver {
    val isForeground: StateFlow<Boolean>
}

class ProcessAppVisibilityObserver : AppVisibilityObserver, DefaultLifecycleObserver {
    private val foregroundState = MutableStateFlow(false)

    override val isForeground: StateFlow<Boolean> = foregroundState.asStateFlow()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        foregroundState.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        foregroundState.value = false
    }
}

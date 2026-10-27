package com.minimalpairs.app.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.create
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.pause
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.start
import com.arkivanov.essenty.lifecycle.stop

/** Connects Compose's native host lifecycle to Decompose's resource-owning components. */
internal class HostLifecycleBridge(private val host: Lifecycle) {
    val lifecycle = LifecycleRegistry(com.arkivanov.essenty.lifecycle.Lifecycle.State.CREATED)
    private var attached = false
    private var closed = false
    private val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_CREATE -> lifecycle.create()
            Lifecycle.Event.ON_START -> lifecycle.start()
            Lifecycle.Event.ON_RESUME -> lifecycle.resume()
            Lifecycle.Event.ON_PAUSE -> lifecycle.pause()
            Lifecycle.Event.ON_STOP -> lifecycle.stop()
            Lifecycle.Event.ON_DESTROY -> lifecycle.destroy()
            Lifecycle.Event.ON_ANY -> Unit
        }
    }

    fun attach() {
        if (attached || closed) return
        attached = true
        if (host.currentState == Lifecycle.State.DESTROYED) lifecycle.destroy()
        else host.addObserver(observer) // Replays events up to the current native state.
    }

    fun close() {
        if (closed) return
        closed = true
        if (attached) host.removeObserver(observer)
        attached = false
        lifecycle.destroy()
    }
}

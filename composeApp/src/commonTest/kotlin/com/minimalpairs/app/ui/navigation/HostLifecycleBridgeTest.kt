package com.minimalpairs.app.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.lifecycle.doOnStop
import kotlin.test.Test
import kotlin.test.assertEquals
import com.arkivanov.essenty.lifecycle.Lifecycle as ComponentLifecycle

class HostLifecycleBridgeTest {
    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    @Test
    fun backgroundStopsComponentsAndForegroundResumesTheSameLifecycle() {
        val owner = Owner()
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val bridge = HostLifecycleBridge(owner.lifecycle)
        var stops = 0
        var destroys = 0
        bridge.lifecycle.doOnStop { stops++ }
        bridge.lifecycle.doOnDestroy { destroys++ }
        bridge.attach()
        bridge.attach()
        assertEquals(ComponentLifecycle.State.RESUMED, bridge.lifecycle.state)
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        assertEquals(ComponentLifecycle.State.CREATED, bridge.lifecycle.state)
        assertEquals(1, stops)
        assertEquals(0, destroys)
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        assertEquals(ComponentLifecycle.State.RESUMED, bridge.lifecycle.state)
        bridge.close()
        bridge.close()
        assertEquals(2, stops)
        assertEquals(1, destroys)
        assertEquals(ComponentLifecycle.State.DESTROYED, bridge.lifecycle.state)
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        bridge.attach()
        assertEquals(ComponentLifecycle.State.DESTROYED, bridge.lifecycle.state)
    }

    @Test
    fun disposalBeforeAttachStillReleasesComponentResources() {
        val bridge = HostLifecycleBridge(Owner().lifecycle)
        var destroyed = 0
        bridge.lifecycle.doOnDestroy { destroyed++ }
        bridge.close()
        assertEquals(1, destroyed)
    }

    @Test
    fun alreadyDestroyedHostCannotStartComponents() {
        val owner = Owner()
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        owner.lifecycle.currentState = Lifecycle.State.DESTROYED
        val bridge = HostLifecycleBridge(owner.lifecycle)
        bridge.attach()
        assertEquals(ComponentLifecycle.State.DESTROYED, bridge.lifecycle.state)
        bridge.close()
    }
}

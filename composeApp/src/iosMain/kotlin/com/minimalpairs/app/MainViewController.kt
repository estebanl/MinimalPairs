package com.minimalpairs.app

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.arkivanov.decompose.DefaultComponentContext
import com.minimalpairs.app.ui.navigation.DefaultRootComponent
import com.minimalpairs.app.ui.navigation.HostLifecycleBridge
import com.minimalpairs.app.ui.screens.RootScreen

@Suppress("unused", "FunctionName")
fun MainViewController() = ComposeUIViewController {
    val owner = LocalLifecycleOwner.current
    val bridge = remember(owner) { HostLifecycleBridge(owner.lifecycle) }
    val root = remember(bridge) {
        DefaultRootComponent(componentContext = DefaultComponentContext(lifecycle = bridge.lifecycle))
    }
    DisposableEffect(bridge) {
        bridge.attach()
        onDispose { bridge.close() }
    }
    RootScreen(component = root)
}

package com.minimalpairs.app

import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.minimalpairs.app.ui.navigation.DefaultRootComponent
import com.minimalpairs.app.ui.screens.RootScreen

@Suppress("unused", "FunctionName")
fun MainViewController() = ComposeUIViewController {
    val lifecycle = LifecycleRegistry()
    lifecycle.resume()
    val root = DefaultRootComponent(
        componentContext = DefaultComponentContext(lifecycle = lifecycle)
    )
    RootScreen(component = root)
}

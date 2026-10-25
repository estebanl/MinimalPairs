package com.minimalpairs.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.minimalpairs.app.ui.navigation.DefaultRootComponent
import com.minimalpairs.app.ui.screens.RootScreen

fun main() = application {
    val lifecycle = LifecycleRegistry()
    lifecycle.resume()

    val root = DefaultRootComponent(
        componentContext = DefaultComponentContext(lifecycle = lifecycle)
    )

    val windowState = rememberWindowState(width = 1000.dp, height = 800.dp)

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Ship or Sheep? - Modern Pronunciation Lab"
    ) {
        RootScreen(component = root)
    }
}

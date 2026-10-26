package com.minimalpairs.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.arkivanov.decompose.ComponentContext
import com.minimalpairs.app.data.local.AndroidContextHolder
import com.minimalpairs.app.ui.navigation.DefaultRootComponent
import com.minimalpairs.app.ui.navigation.RootComponent
import com.minimalpairs.app.ui.screens.RootScreen

/** The supplied component context must follow the host Activity's lifecycle. */
fun createAndroidRootComponent(context: Context, componentContext: ComponentContext): RootComponent {
    // Keep only the application context, and initialize it before opening progress storage.
    AndroidContextHolder.context = context.applicationContext
    return DefaultRootComponent(componentContext)
}

/** Android library entry point for a host that owns a lifecycle-aware Decompose context. */
@Composable
fun MinimalPairsApp(componentContext: ComponentContext, modifier: Modifier = Modifier) {
    val applicationContext = LocalContext.current.applicationContext
    val root = remember(applicationContext, componentContext) {
        createAndroidRootComponent(applicationContext, componentContext)
    }
    RootScreen(root, modifier)
}

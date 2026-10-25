package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.minimalpairs.app.ui.navigation.RootChild
import com.minimalpairs.app.ui.navigation.RootComponent
import com.minimalpairs.app.ui.theme.MinimalPairsTheme

@Composable
fun RootScreen(
    component: RootComponent,
    modifier: Modifier = Modifier
) {
    MinimalPairsTheme {
        val childStack by component.childStack.subscribeAsState()
        val activeChild = childStack.active.instance

        val selectedTab = when (activeChild) {
            is RootChild.Dashboard, is RootChild.EarTrainer -> 0
            is RootChild.ArticulatorLab -> 1
            is RootChild.ConfusionMatrix -> 2
            is RootChild.MeaningTrap -> 3
            is RootChild.Prosody -> 4
        }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = component::onNavigateToDashboard,
                        icon = { Text("👂") },
                        label = { Text("Ear Trainer") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { component.onNavigateToArticulatorLab() },
                        icon = { Text("🎙️") },
                        label = { Text("Articulator") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = component::onNavigateToConfusionMatrix,
                        icon = { Text("📊") },
                        label = { Text("Matrix") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = component::onNavigateToMeaningTrap,
                        icon = { Text("💬") },
                        label = { Text("Comic") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = component::onNavigateToProsody,
                        icon = { Text("〰️") },
                        label = { Text("Prosody") }
                    )
                }
            },
            modifier = modifier.fillMaxSize()
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Children(
                    stack = component.childStack,
                    animation = stackAnimation(fade())
                ) { child ->
                    when (val instance = child.instance) {
                        is RootChild.Dashboard -> DashboardScreen(instance.component)
                        is RootChild.EarTrainer -> EarTrainerScreen(instance.component)
                        is RootChild.ArticulatorLab -> ArticulatorLabScreen(instance.component)
                        is RootChild.ConfusionMatrix -> ConfusionMatrixScreen(instance.component)
                        is RootChild.MeaningTrap -> MeaningTrapScreen(instance.component)
                        is RootChild.Prosody -> ProsodyScreen(instance.component)
                    }
                }
            }
        }
    }
}

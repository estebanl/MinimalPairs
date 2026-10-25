package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.minimalpairs.app.ui.components.PracticeIcon
import com.minimalpairs.app.ui.navigation.RootChild
import com.minimalpairs.app.ui.navigation.RootComponent
import com.minimalpairs.app.ui.theme.MinimalPairsTheme

@Composable
fun RootScreen(component: RootComponent, modifier: Modifier = Modifier) {
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
        val labels = listOf("Listen", "Speak", "Progress", "Stories", "Rhythm")
        val icons = listOf(PracticeIcon.Listen, PracticeIcon.Speak, PracticeIcon.Progress, PracticeIcon.Stories, PracticeIcon.Rhythm)
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (activeChild !is RootChild.EarTrainer) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = {
                                    when (index) {
                                        0 -> component.onNavigateToDashboard()
                                        1 -> component.onNavigateToArticulatorLab()
                                        2 -> component.onNavigateToConfusionMatrix()
                                        3 -> component.onNavigateToMeaningTrap()
                                        4 -> component.onNavigateToProsody()
                                    }
                                },
                                icon = { PracticeIcon(icons[index]) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            },
            modifier = modifier.fillMaxSize()
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
                Children(
                    stack = component.childStack,
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                    animation = stackAnimation(fade())
                ) { child ->
                    when (val instance = child.instance) {
                        is RootChild.Dashboard -> DashboardScreen(instance.component)
                        is RootChild.EarTrainer -> EarTrainerScreen(instance.component)
                        is RootChild.ArticulatorLab -> ArticulatorLabScreen(instance.component)
                        is RootChild.ConfusionMatrix -> ConfusionMatrixScreen(instance.component, onPractice = component::onNavigateToDashboard)
                        is RootChild.MeaningTrap -> MeaningTrapScreen(instance.component)
                        is RootChild.Prosody -> ProsodyScreen(instance.component)
                    }
                }
            }
        }
    }
}

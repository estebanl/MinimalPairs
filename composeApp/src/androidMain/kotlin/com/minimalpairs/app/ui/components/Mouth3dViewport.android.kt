package com.minimalpairs.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.minimalpairs.app.ui.reference.Mouth3dState

@Composable
actual fun mouth3dAvailable(): Boolean = false

@Composable
actual fun Mouth3dViewport(state: Mouth3dState, modifier: Modifier, onError: () -> Unit) = Unit

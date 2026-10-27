package com.minimalpairs.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.minimalpairs.app.ui.reference.Mouth3dState

/** False on other platforms and while the iOS screen reader needs image descriptions. */
@Composable
expect fun mouth3dAvailable(): Boolean

@Composable
expect fun Mouth3dViewport(state: Mouth3dState, modifier: Modifier, onError: () -> Unit)

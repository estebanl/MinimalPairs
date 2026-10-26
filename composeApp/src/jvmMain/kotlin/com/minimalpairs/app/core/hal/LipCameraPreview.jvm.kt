package com.minimalpairs.app.core.hal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.minimalpairs.app.core.cv.LipCameraFrame
import com.minimalpairs.app.core.cv.LipCameraStatus

@Composable
actual fun LipCameraPreview(modifier: Modifier, onFrame: (LipCameraFrame) -> Unit) {
    LaunchedEffect(Unit) { onFrame(LipCameraFrame(LipCameraStatus.UNAVAILABLE)) }
}

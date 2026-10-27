package com.minimalpairs.app.core.hal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.minimalpairs.app.core.cv.LipCameraFrame

/** Owns camera permission, preview, and on-device analysis for as long as it is composed. */
@Composable
expect fun LipCameraPreview(modifier: Modifier, onFrame: (LipCameraFrame) -> Unit)

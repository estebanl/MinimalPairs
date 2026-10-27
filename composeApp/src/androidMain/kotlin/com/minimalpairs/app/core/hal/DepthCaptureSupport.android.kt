package com.minimalpairs.app.core.hal

/** The active CameraX/ML Kit contour backend does not supply calibrated front depth. */
actual fun inspectDepthCaptureSupport(): DepthCaptureSupport = DepthCaptureSupport.NO_CALIBRATED_SOURCE

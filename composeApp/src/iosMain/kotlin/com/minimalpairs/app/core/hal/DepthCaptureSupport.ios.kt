package com.minimalpairs.app.core.hal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AVFoundation.*

@OptIn(ExperimentalForeignApi::class)
actual fun inspectDepthCaptureSupport(): DepthCaptureSupport {
    val device = AVCaptureDevice.defaultDeviceWithDeviceType(AVCaptureDeviceTypeBuiltInTrueDepthCamera,
        mediaType = AVMediaTypeVideo, position = AVCaptureDevicePositionFront)
        ?: return DepthCaptureSupport.NO_CALIBRATED_SOURCE
    return if (device.formats.filterIsInstance<AVCaptureDeviceFormat>().any { it.supportedDepthDataFormats.isNotEmpty() })
        DepthCaptureSupport.CANDIDATE_REQUIRES_VALIDATION else DepthCaptureSupport.NO_CALIBRATED_SOURCE
}

enum class IosDepthMetadataAssessment {
    RELATIVE_ONLY, MISSING_CALIBRATION, INVALID_CALIBRATION,
    /** An input precondition only; synchrony, registration, rectification and landmarks remain unchecked. */
    ABSOLUTE_REQUIRES_REGISTRATION
}

/** Do not treat a DepthFloat pixel format, TrueDepth model name, or this result as metric validation. */
@OptIn(ExperimentalForeignApi::class)
fun assessIosDepthMetadata(data: AVDepthData): IosDepthMetadataAssessment {
    if (data.depthDataAccuracy != AVDepthDataAccuracyAbsolute) return IosDepthMetadataAssessment.RELATIVE_ONLY
    val calibration = data.cameraCalibrationData ?: return IosDepthMetadataAssessment.MISSING_CALIBRATION
    val dimensionsValid = calibration.intrinsicMatrixReferenceDimensions.useContents {
        width.isFinite() && height.isFinite() && width > 0.0 && height > 0.0
    }
    if (!dimensionsValid || calibration.inverseLensDistortionLookupTable == null) {
        return IosDepthMetadataAssessment.INVALID_CALIBRATION
    }
    return IosDepthMetadataAssessment.ABSOLUTE_REQUIRES_REGISTRATION
}

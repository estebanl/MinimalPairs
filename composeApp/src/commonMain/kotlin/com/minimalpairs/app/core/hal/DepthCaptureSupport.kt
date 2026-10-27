package com.minimalpairs.app.core.hal

enum class DepthCaptureSupport {
    NO_CALIBRATED_SOURCE,
    /** Hardware is a candidate only. No frame registration or physical accuracy is implied. */
    CANDIDATE_REQUIRES_VALIDATION
}

/** Read-only capability discovery; never opens a second camera or grants metric UI eligibility. */
expect fun inspectDepthCaptureSupport(): DepthCaptureSupport

package com.minimalpairs.app.ui.reference

import com.minimalpairs.app.core.model.PhoneticBounds

/** A schematic formant plane, never a reconstruction or deformation of a learner's anatomy. */
object AcousticPlacementComparison {
    data class Observation(val f1: Float, val f2: Float, val point: OralPoint)

    fun observation(f1: Float?, f2: Float?): Observation? {
        if (f1 == null || f2 == null || !f1.isFinite() || !f2.isFinite() ||
            f1 !in 150f..1500f || f2 !in 500f..4000f || f2 <= f1) return null
        return Observation(f1, f2, point(f1, f2))
    }

    // Increasing height = decreasing F1; increasing frontness = increasing F2.
    // Coordinates are arbitrary display units, not tongue coordinates or millimetres.
    fun point(f1: Float, f2: Float) = OralPoint(
        0f, 1f - 2f * (f1 - 150f) / 1350f, 2f * (f2 - 500f) / 3500f - 1f
    )

    fun validBounds(bounds: PhoneticBounds): Boolean =
        observation(bounds.f1MinHz, bounds.f2MinHz) != null &&
            observation(bounds.f1MaxHz, bounds.f2MaxHz) != null &&
            bounds.f1MinHz <= bounds.f1MaxHz && bounds.f2MinHz <= bounds.f2MaxHz

    fun guidance(observation: Observation, bounds: PhoneticBounds): String {
        val cues = mutableListOf<String>()
        if (observation.f1 > bounds.f1MaxHz) cues += "Try a slightly higher tongue position."
        else if (observation.f1 < bounds.f1MinHz) cues += "Try a slightly lower tongue position."
        if (observation.f2 < bounds.f2MinHz) cues += "Try moving your tongue forward."
        else if (observation.f2 > bounds.f2MaxHz) cues += "Try moving your tongue back."
        return cues.joinToString(" ").ifEmpty { "Your measured formants are inside the target range." }
    }
}

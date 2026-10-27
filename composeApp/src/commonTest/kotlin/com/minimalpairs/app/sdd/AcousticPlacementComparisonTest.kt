package com.minimalpairs.app.sdd

import com.minimalpairs.app.core.model.PhoneticBounds
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import com.minimalpairs.app.ui.reference.AcousticPlacementComparison
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AcousticPlacementComparisonTest {
    @Test
    fun invalidAndAbsentMeasurementsDoNotProduceAnObservedPosition() {
        for ((f1, f2) in listOf(null to 2200f, 300f to null, 0f to 0f, Float.NaN to 2200f,
            300f to Float.POSITIVE_INFINITY, 1600f to 2200f, 300f to 4500f, 900f to 800f)) {
            assertNull(AcousticPlacementComparison.observation(f1, f2))
        }
    }

    @Test
    fun lowerF1MovesUpAndHigherF2MovesForwardWithoutInventingLateralPosition() {
        val low = assertNotNull(AcousticPlacementComparison.observation(800f, 1200f))
        val high = assertNotNull(AcousticPlacementComparison.observation(300f, 2400f))
        assertTrue(high.point.y > low.point.y)
        assertTrue(high.point.z > low.point.z)
        assertEquals(0f, high.point.x)
        assertEquals(0f, low.point.x)
    }

    @Test
    fun everyCatalogTargetCanBeComparedAndItsCenterIsInRange() {
        VowelContrastsCatalog.defaultContrasts.forEach { contrast ->
            listOf(contrast.boundsPhoneme1, contrast.boundsPhoneme2).forEach { bounds ->
                assertTrue(AcousticPlacementComparison.validBounds(bounds), contrast.id)
                val center = assertNotNull(AcousticPlacementComparison.observation(
                    (bounds.f1MinHz + bounds.f1MaxHz) / 2, (bounds.f2MinHz + bounds.f2MaxHz) / 2))
                assertEquals("Your measured formants are inside the target range.",
                    AcousticPlacementComparison.guidance(center, bounds))
            }
        }
    }

    @Test
    fun guidanceUsesSelectedTargetBoundsAndRejectsInvalidTargets() {
        val bounds = PhoneticBounds(250f, 380f, 2100f, 2600f)
        val lowerBack = assertNotNull(AcousticPlacementComparison.observation(500f, 1800f))
        assertEquals("Try a slightly higher tongue position. Try moving your tongue forward.",
            AcousticPlacementComparison.guidance(lowerBack, bounds))
        val higherFront = assertNotNull(AcousticPlacementComparison.observation(200f, 2800f))
        assertEquals("Try a slightly lower tongue position. Try moving your tongue back.",
            AcousticPlacementComparison.guidance(higherFront, bounds))
        assertFalse(AcousticPlacementComparison.validBounds(bounds.copy(f1MaxHz = 200f)))
        assertFalse(AcousticPlacementComparison.validBounds(bounds.copy(f2MinHz = Float.NaN)))
    }
}

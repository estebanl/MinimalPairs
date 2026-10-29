package com.minimalpairs.app.sdd

import com.minimalpairs.app.ui.reference.*
import kotlin.test.*

class AuthoredOralSectionsTest {
    private val n = OralPoint(0f, 0f, 1f)
    private fun crossing(part: OralPart) = OralTriangle(
        OralPoint(-1f, 0f, 0f), OralPoint(1f, 0f, 0f), OralPoint(0f, 1f, 0f), part, n, n, n)
    private fun areaSquared(face: OralTriangle): Float {
        val cross = (face.b - face.a).cross(face.c - face.a)
        return cross.x * cross.x + cross.y * cross.y + cross.z * cross.z
    }

    @Test fun authoredModeClipsAllMaterialsIncludingTongueOnBothSides() {
        val mesh = OralPart.entries.map(::crossing)
        for (left in listOf(true, false)) {
            val sign = if (left) 1 else -1
            val clipped = OralCavityModel.cutaway(mesh, true, left, authoredSections = true)
            assertEquals(OralPart.entries.toSet(), clipped.map { it.part }.toSet())
            assertEquals(OralPart.entries.size, clipped.size)
            assertTrue(clipped.all { areaSquared(it) > 1e-12f })
            assertTrue(clipped.flatMap { listOf(it.a, it.b, it.c) }.all { it.x * sign >= 0f })
            assertTrue(clipped.flatMap { listOf(it.na, it.nb, it.nc) }.all {
                it.x.isFinite() && it.y.isFinite() && it.z.isFinite()
            })
        }
    }

    @Test fun authoredModeNeverFallsBackToOppositeSideTongueOrAddsCaps() {
        val tongue = crossing(OralPart.TONGUE)
        for (left in listOf(true, false)) {
            val clipped = OralCavityModel.cutaway(listOf(tongue), true, left, authoredSections = true)
            assertEquals(1, clipped.size)
            assertFalse(clipped.any { it.isSection }) // No synthesized triangle was added.
            assertNotEquals(listOf(tongue), clipped)
            assertEquals(listOf(tongue), OralCavityModel.cutaway(listOf(tongue), true, left))
        }
    }

    @Test fun existingAuthoredMidlineSurfaceAndMetadataArePreserved() {
        val normal = OralPoint(1f, 0f, 0f)
        val cap = OralTriangle(OralPoint(0f, 0f, 0f), OralPoint(0f, 1f, 0f), OralPoint(0f, 0f, 1f),
            OralPart.TONGUE, normal, normal, normal, sourceSurface = 7, isSection = true)
        for (left in listOf(true, false)) {
            assertEquals(listOf(cap), OralCavityModel.cutaway(listOf(cap), true, left, authoredSections = true))
        }
    }

    @Test fun yawSignSwitchClipsOppositeHalfAndInvalidatesFrameKey() {
        val mesh = listOf(crossing(OralPart.TONGUE))
        val positive = OralCavityModel.cutaway(mesh, true, fromLeft = .1f >= 0, authoredSections = true)
        val negative = OralCavityModel.cutaway(mesh, true, fromLeft = -.1f >= 0, authoredSections = true)
        assertTrue(positive.flatMap { listOf(it.a, it.b, it.c) }.any { it.x > 0 })
        assertTrue(negative.flatMap { listOf(it.a, it.b, it.c) }.any { it.x < 0 })
        assertNotEquals(articulationFrameKey("p", "atlas", 1, true, true), articulationFrameKey("p", "atlas", 1, true, false))
    }

    @Test fun disabledCutawayReturnsExactMeshAndIncompatibleModesFail() {
        val mesh = listOf(crossing(OralPart.TONGUE))
        assertSame(mesh, OralCavityModel.cutaway(mesh, false, authoredSections = true))
        assertFailsWith<IllegalArgumentException> {
            OralCavityModel.cutaway(mesh, true, sectionClosedTongue = true, authoredSections = true)
        }
    }
}

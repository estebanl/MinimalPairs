package com.minimalpairs.app.sdd

import com.minimalpairs.app.ui.reference.*
import kotlin.test.*

class OralRenderedFrameTest {
    private fun mesh(): List<OralTriangle> {
        val n = OralPoint(0f, 0f, 1f)
        return listOf(OralTriangle(OralPoint(0f,0f,0f), OralPoint(1f,0f,0f), OralPoint(0f,1f,0f), OralPart.TONGUE,n,n,n))
    }

    @Test fun dragRetainsLastCompletedFrameForIdenticalSelectionAndMesh() {
        val mesh = mesh()
        val frame = OralRenderedFrame("r:bunched", mesh, OralView(yaw=90f), "completed pixels")
        val retained = frame.forMesh("r:bunched", mesh)
        assertSame(frame, retained)
        assertEquals("completed pixels", retained?.image)
        // The displayed view remains honest while a newer requested view is unfinished.
        assertNotEquals(OralView(yaw=75f), retained?.view)
        assertEquals(90f, retained?.view?.yaw)
    }

    @Test fun phonemeOrPhaseChangeCannotRetainPreviousPixelsEvenWithSharedGeometry() {
        val mesh = mesh()
        val frame = OralRenderedFrame("p:closure", mesh, OralView(), "previous pixels")
        assertNull(frame.forMesh("b:closure", mesh))
        assertNull(frame.forMesh("p:release", mesh))
    }

    @Test fun replacedOrDifferentCutawayMeshCannotRetainPreviousPixels() {
        val mesh = mesh()
        val frame = OralRenderedFrame("r:bunched", mesh, OralView(), "previous pixels")
        assertNull(frame.forMesh("r:bunched", mesh.toMutableList()))
        assertNull(frame.forMesh("r:bunched", emptyList()))
    }
}

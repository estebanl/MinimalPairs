package com.minimalpairs.app.sdd

import com.minimalpairs.app.ui.reference.*
import kotlin.test.*

class OralMaterialPaletteTest {
    @Test fun persistedOriginalMaterialIdsRemainUnchanged() {
        assertEquals(listOf("PALATE", "TONGUE", "TEETH", "LIPS", "TISSUE"), OralPart.entries.take(5).map { it.name })
        assertEquals(listOf(5, 6, 7, 8), listOf(OralPart.CAVITY, OralPart.MUCOSA, OralPart.BONE, OralPart.CARTILAGE).map { it.ordinal })
    }

    @Test fun everyMaterialRendersAndCavityIsDarkerThanMucosa() {
        val n = OralPoint(0f, 0f, 1f)
        fun pixel(part: OralPart): Int {
            val triangle = OralTriangle(OralPoint(-.7f, -.6f, 0f), OralPoint(.7f, -.6f, 0f), OralPoint(0f, .8f, 0f), part, n, n, n)
            return OralRasterizer.render(listOf(triangle), OralView(0f), 64, 64).argb[32 * 64 + 32]
        }
        val colors = OralPart.entries.map(::pixel)
        assertTrue(colors.all { (it ushr 24) == 255 })
        assertEquals(9, colors.toSet().size)
        fun brightness(rgb: Int) = ((rgb ushr 16) and 255) + ((rgb ushr 8) and 255) + (rgb and 255)
        assertTrue(brightness(pixel(OralPart.CAVITY)) < brightness(pixel(OralPart.MUCOSA)))
    }
}

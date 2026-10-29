package com.minimalpairs.app.sdd

import com.minimalpairs.app.ui.reference.OralRasterizer
import com.minimalpairs.app.ui.reference.toImageBitmap
import kotlin.test.*

class OralRasterImageTest {
    @Test fun portableCanvasTransferPreservesOpaqueColorsAndTransparency() {
        val pixels = intArrayOf(0, 0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt(), 0xffffffff.toInt(), 0)
        val bitmap = OralRasterizer.Frame(3, 2, pixels).toImageBitmap()
        val result = IntArray(6)
        bitmap.readPixels(result)
        assertContentEquals(pixels, result)
    }

    @Test fun bitmapTransferCanBeCancelledBeforeDrawing() {
        assertFailsWith<IllegalStateException> {
            OralRasterizer.Frame(1, 1, intArrayOf(0)).toImageBitmap { error("cancelled") }
        }
    }
}

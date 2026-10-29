package com.minimalpairs.app.ui.reference

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect

/** Portable bitmap transfer: horizontal runs avoid a platform-specific pixel API or a GL dependency. */
fun OralRasterizer.Frame.toImageBitmap(checkActive: () -> Unit = {}): ImageBitmap {
    val image = ImageBitmap(width, height)
    val canvas = androidx.compose.ui.graphics.Canvas(image)
    val paint = Paint().apply { isAntiAlias = false }
    for (y in 0 until height) {
        checkActive()
        var x = 0
        while (x < width) {
            val color = argb[y * width + x]
            val start = x++
            while (x < width && argb[y * width + x] == color) x++
            if (color != 0) {
                paint.color = Color(color)
                canvas.drawRect(Rect(start.toFloat(), y.toFloat(), x.toFloat(), y + 1f), paint)
            }
        }
    }
    return image
}

package com.minimalpairs.app.ui.reference

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

actual fun OralRasterizer.Frame.toImageBitmap(): ImageBitmap {
    // Rasterizer pixels are either fully opaque or fully transparent, so straight and
    // premultiplied alpha coincide. BGRA byte order matches a little-endian ARGB int.
    val bytes = ByteArray(argb.size * 4)
    for (i in argb.indices) {
        val c = argb[i]
        bytes[i * 4] = c.toByte()
        bytes[i * 4 + 1] = (c shr 8).toByte()
        bytes[i * 4 + 2] = (c shr 16).toByte()
        bytes[i * 4 + 3] = (c ushr 24).toByte()
    }
    val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
    return Image.makeRaster(info, bytes, width * 4).toComposeImageBitmap()
}

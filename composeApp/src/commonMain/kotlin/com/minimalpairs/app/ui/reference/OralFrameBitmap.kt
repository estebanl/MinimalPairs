package com.minimalpairs.app.ui.reference

import androidx.compose.ui.graphics.ImageBitmap

/** Copies a straight-ARGB frame into a platform bitmap in one call. */
expect fun OralRasterizer.Frame.toImageBitmap(): ImageBitmap

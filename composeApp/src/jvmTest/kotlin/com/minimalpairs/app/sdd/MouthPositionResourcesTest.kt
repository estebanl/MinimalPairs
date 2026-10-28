package com.minimalpairs.app.sdd

import com.minimalpairs.app.ui.reference.MouthPositionReferences
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import minimalpairs.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MouthPositionResourcesTest {
    @OptIn(ExperimentalResourceApi::class)
    @Test
    fun allIllustrationsLoadFromThePackagedResourcesWithoutNetwork() = runBlocking {
        for ((phoneme, reference) in MouthPositionReferences.byPhoneme) {
            for (view in listOf("front", "side")) {
                val path = "drawable/mouth_${reference.assetKey}_$view.png"
                val bytes = Res.readBytes(path)
                val image = assertNotNull(ImageIO.read(ByteArrayInputStream(bytes)), "$phoneme $view")
                assertTrue(image.width >= 600, "$path must remain sharp at mobile display sizes")
                assertEquals(1.5, image.width.toDouble() / image.height, 0.01, "$path aspect ratio")
                assertTrue(image.colorModel.hasAlpha(), "$path must preserve transparency")
                val alpha = image.alphaRaster
                val values = alpha.getSamples(0, 0, image.width, image.height, 0, null as IntArray?)
                assertTrue(values.any { it == 0 }, "$path must have a transparent background")
                assertTrue(values.any { it >= 240 }, "$path must retain the visible artwork")
            }
        }
    }
}

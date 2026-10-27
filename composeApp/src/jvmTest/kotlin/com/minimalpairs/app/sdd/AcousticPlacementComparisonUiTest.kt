package com.minimalpairs.app.sdd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.minimalpairs.app.core.model.PhoneticBounds
import com.minimalpairs.app.ui.components.AcousticPlacementComparisonCard
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class AcousticPlacementComparisonUiTest {
    private val target = PhoneticBounds(250f, 380f, 2100f, 2600f)

    @Test
    fun observedSoundAndMissingMeasurementHaveAccessibleStates() {
        val f1 = mutableStateOf<Float?>(null)
        val recording = mutableStateOf(false)
        val bounds = mutableStateOf(target)
        ImageComposeScene(390, 1000) {
            MinimalPairsTheme {
                AcousticPlacementComparisonCard(bounds.value, f1.value, 1800f, recording.value)
            }
        }.use { scene ->
            scene.render().close()
            assertTrue(scene.texts().contains("Record a vowel to compare with the target."))
            recording.value = true
            f1.value = 500f
            Snapshot.sendApplyNotifications()
            scene.render().close()
            assertTrue(scene.texts().any { it.startsWith("Live F1 500 Hz, F2 1800 Hz.") })
            assertTrue(scene.texts().any { it.contains("Try moving your tongue forward.") })
            bounds.value = PhoneticBounds(400f, 600f, 1600f, 2000f)
            Snapshot.sendApplyNotifications()
            scene.render().close()
            assertTrue(scene.texts().any { it.contains("inside the target range") })
            recording.value = false
            Snapshot.sendApplyNotifications()
            scene.render().close()
            assertTrue(scene.texts().any { it.startsWith("Recorded F1 500 Hz") })
            recording.value = true
            f1.value = 0f
            Snapshot.sendApplyNotifications()
            scene.render().close()
            assertTrue(scene.texts().contains("No clear vowel measurement. Say the vowel to compare."))
            assertFalse(scene.texts().any { it.startsWith("Live F1") })
        }
    }

    @Test
    fun narrowAndWideLayoutsExposeMeasurementsWithoutTextOverflow() {
        for ((width, fontScale) in listOf(272 to 1f, 272 to 1.8f, 720 to 1f)) {
            for (dark in listOf(false, true)) {
                ImageComposeScene(width, 1800, density = Density(1f, fontScale)) {
                    MinimalPairsTheme(darkTheme = dark) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Box(Modifier.fillMaxSize().padding(16.dp)) {
                                AcousticPlacementComparisonCard(target, 500f, 1800f, true)
                            }
                        }
                    }
                }.use { scene ->
                    scene.render().close()
                    val image = scene.render()
                    val file = File("build/reports/mouth-position/comparison-$width-$fontScale-$dark.png")
                    file.parentFile.mkdirs()
                    file.writeBytes(image.encodeToData()!!.bytes)
                    image.close()
                    assertTrue(scene.nodes().any { node ->
                        node.config.getOrNull(SemanticsProperties.ContentDescription)?.any {
                            it.contains("Live F1 500 Hz, F2 1800 Hz")
                        } == true
                    })
                    val layouts = mutableListOf<TextLayoutResult>()
                    scene.nodes().forEach { it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) }
                    assertTrue(layouts.isNotEmpty())
                    layouts.forEach { layout ->
                        for (line in 0 until layout.lineCount) {
                            assertFalse(layout.isLineEllipsized(line))
                            assertTrue(layout.getLineRight(line) <= layout.size.width + 1f, layout.layoutInput.text.text)
                            assertTrue(layout.getLineBottom(line) <= layout.size.height + 1f, layout.layoutInput.text.text)
                        }
                    }
                }
            }
        }
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        return semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    }

    private fun ImageComposeScene.texts() = nodes().flatMap {
        it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text }
    }
}

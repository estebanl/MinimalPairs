package com.minimalpairs.app.sdd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.minimalpairs.app.core.cv.*
import com.minimalpairs.app.ui.components.LipCameraFeedbackCard
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class LipCameraUiTest {
    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    }
    private val sample = LipMeasurement(.2f,
        listOf(LipPoint(-.5f, 0f), LipPoint(0f, -.2f), LipPoint(.5f, 0f), LipPoint(0f, .2f)),
        listOf(LipPoint(-.4f, 0f), LipPoint(0f, -.1f), LipPoint(.4f, 0f), LipPoint(0f, .1f)))

    @Test
    fun offDeniedAndTrackingStatesRenderAtNarrowWidthsWithoutOverflow() {
        for (scale in listOf(1f, 1.8f)) for (dark in listOf(false, true)) {
            val controller = LipCameraController()
            val owner = Owner()
            ImageComposeScene(300, 2200, density = Density(1f, scale)) {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    MinimalPairsTheme(darkTheme = dark) {
                        LipCameraFeedbackCard("uː", "fool", controller = controller,
                            preview = { modifier, _ -> Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) })
                    }
                }
            }.use { scene ->
                scene.render().close()
                assertTrue(scene.texts().contains("Start camera"))
                assertEquals(LipCameraStatus.OFF, controller.state.value.status)
                scene.click("Start camera")
                scene.render().close()
                controller.accept(controller.state.value.session, LipCameraFrame(LipCameraStatus.TRACKING, sample))
                scene.render().close()
                val image = scene.render()
                val file = File("build/reports/lip-camera/tracking-$scale-$dark.png")
                file.parentFile.mkdirs()
                file.writeBytes(image.encodeToData()!!.bytes)
                image.close()
                assertTrue(scene.texts().contains("Lip opening: 20% of mouth width"))
                val layouts = mutableListOf<TextLayoutResult>()
                scene.nodes().forEach { it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) }
                for (layout in layouts) for (line in 0 until layout.lineCount) {
                    assertFalse(layout.isLineEllipsized(line), layout.layoutInput.text.text)
                    assertTrue(layout.getLineRight(line) <= layout.size.width + 1f, layout.layoutInput.text.text)
                }
                controller.accept(controller.state.value.session, LipCameraFrame(LipCameraStatus.NO_FACE))
                scene.render().close()
                assertFalse(scene.texts().any { it.startsWith("Lip opening:") })
                controller.accept(controller.state.value.session, LipCameraFrame(LipCameraStatus.PERMISSION_DENIED))
                scene.render().close()
                assertTrue(scene.texts().any { it.contains("Camera access is off") })
                assertTrue(scene.texts().contains("Start camera"))
            }
        }
    }

    @Test
    fun backgroundingAndDisposalStopTheSessionAndRejectLateResults() {
        val owner = Owner()
        val controller = LipCameraController()
        var token = 0L
        ImageComposeScene(390, 1800) {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                MinimalPairsTheme {
                    LipCameraFeedbackCard("iː", "sheep", controller = controller, preview = { _, _ -> })
                }
            }
        }.use { scene ->
            scene.render().close()
            scene.click("Start camera")
            scene.render().close()
            token = controller.state.value.session
            controller.accept(token, LipCameraFrame(LipCameraStatus.TRACKING, sample))
            scene.render().close()
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            scene.render().close()
            assertEquals(LipCameraStatus.PAUSED, controller.state.value.status)
            assertNull(controller.state.value.measurement)
            controller.accept(token, LipCameraFrame(LipCameraStatus.TRACKING, sample))
            assertNull(controller.state.value.measurement)
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            scene.render().close()
            assertEquals(LipCameraStatus.PAUSED, controller.state.value.status)
            scene.click("Start camera")
            scene.render().close()
            token = controller.state.value.session
        }
        assertEquals(LipCameraStatus.OFF, controller.state.value.status)
        controller.accept(token, LipCameraFrame(LipCameraStatus.TRACKING, sample))
        assertNull(controller.state.value.measurement)
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        return semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    }
    private fun ImageComposeScene.texts() = nodes().mapNotNull { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
    }
    private fun ImageComposeScene.click(text: String) {
        val node = nodes().first { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } == text &&
                node.config.getOrNull(SemanticsActions.OnClick) != null
        }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }
}

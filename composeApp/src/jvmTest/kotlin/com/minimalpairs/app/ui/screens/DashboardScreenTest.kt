package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.core.model.ListeningContrast
import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.ui.navigation.DashboardComponent
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real Compose renders; screenshots are review artifacts rather than golden baselines. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DashboardScreenTest {
    @Test
    fun libraryPreservesCatalogOrderAndEveryRowOpensItsContrast() = onUiThread {
        val contrasts = ListeningContrastsCatalog.defaultContrasts.reversed()
        Harness(Configuration("library", 430, 1800), contrasts = contrasts).use { harness ->
            harness.settle()
            assertTrue(harness.hasText("20 contrasts"))
            assertEquals(contrasts.map(::practiceLabel), harness.practiceButtons().map { it.config[SemanticsActions.OnClick].label })
            for (contrast in contrasts) {
                harness.practiceButton(contrast).config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals(contrast.id, harness.component.selected)
            }
            assertFalse(harness.hasText("All 20 contrasts"))
            assertFalse(harness.hasText("Show fewer"))
        }
    }

    @Test
    fun scrollingReachesTheLastContrastAndRestoresItsPosition() = onUiThread {
        val configuration = Configuration("restore", 320, 640)
        val last = ListeningContrastsCatalog.defaultContrasts.last()
        val saved = Harness(configuration).use { harness ->
            harness.settle()
            harness.scrollTo(20)
            assertTrue(harness.practiceButton(last).boundsInRoot.height > 0)
            harness.registry.performSave()
        }
        Harness(configuration, restoredValues = saved).use { harness ->
            harness.settle()
            assertTrue(harness.practiceButton(last).boundsInRoot.height > 0)
            assertTrue(harness.practiceButton(last).config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(last.id, harness.component.selected)
            assertFalse(harness.hasText("All 20 contrasts"))
            assertFalse(harness.hasText("Show fewer"))
        }
    }

    @Test
    fun highlightFollowsTheLastOpenedContrastAndSurvivesScreenRestoration() = onUiThread {
        val configuration = Configuration("highlight", 430, 820)
        val contrasts = ListeningContrastsCatalog.defaultContrasts
        val saved = Harness(configuration).use { harness ->
            harness.settle()
            assertEquals(listOf(practiceLabel(contrasts.first())), harness.highlightedLabels())
            val chosen = contrasts[3]
            assertTrue(harness.practiceButton(chosen).config[SemanticsActions.OnClick].action!!.invoke())
            harness.settle()
            assertEquals(chosen.id, harness.component.selected)
            assertEquals(listOf(practiceLabel(chosen)), harness.highlightedLabels())
            harness.capture("moved")

            harness.scrollTo(20)
            assertTrue(harness.practiceButton(contrasts.last()).config[SemanticsActions.OnClick].action!!.invoke())
            harness.settle()
            assertEquals(contrasts.last().id, harness.component.selected)
            assertEquals(listOf(practiceLabel(contrasts.last())), harness.highlightedLabels())
            harness.registry.performSave()
        }
        Harness(configuration, restoredValues = saved).use { harness ->
            harness.settle()
            assertEquals(listOf(practiceLabel(contrasts.last())), harness.highlightedLabels())
            harness.capture("restored")
            harness.scrollTo(0)
            assertFalse(harness.practiceButton(contrasts.first()).config[SemanticsProperties.Selected])
            assertTrue(harness.practiceButton(contrasts[1]).config[SemanticsActions.OnClick].action!!.invoke())
            harness.settle()
            assertEquals(listOf(practiceLabel(contrasts[1])), harness.highlightedLabels())
        }
    }

    @Test
    fun catalogCountsHandleEmptySingleAndLargerCatalogs() = onUiThread {
        for (count in listOf(0, 1, 8, 9)) {
            val contrasts = ListeningContrastsCatalog.defaultContrasts.take(count)
            Harness(Configuration("count-$count", 430, 1000), contrasts = contrasts).use { harness ->
                harness.settle()
                assertTrue(harness.hasText("$count ${if (count == 1) "contrast" else "contrasts"}"))
                assertEquals(count, harness.practiceButtons().size)
                assertEquals(count == 0, harness.hasText("Practice sounds will appear here when available."))
                assertFalse(harness.hasText("All $count contrasts"))
                assertFalse(harness.hasText("Show fewer"))
                if (count == 1) {
                    harness.practiceButton(contrasts.single()).config[SemanticsActions.OnClick].action!!.invoke()
                    assertEquals(contrasts.single().id, harness.component.selected)
                }
            }
        }
    }

    @Test
    fun layoutsRenderInBothThemesWithLargeTextAndKeyboardAccess() = onUiThread {
        val configurations = listOf(
            Configuration("compact", 320, 640),
            Configuration("phone", 430, 820),
            Configuration("desktop", 1000, 800),
            Configuration("large-text", 320, 640, 2f)
        )
        for (configuration in configurations) {
            for (dark in listOf(false, true)) {
                Harness(configuration, dark).use { harness ->
                    harness.settle()
                    harness.capture("initial")
                    assertTrue(harness.hasText("Listen"))
                    assertTrue(harness.nodes().any { it.text() == "Listen" && it.config.contains(SemanticsProperties.Heading) })
                    harness.assertReadableRows()
                    var tabs = 0
                    while (harness.practiceButtons().none { it.config.getOrNull(SemanticsProperties.Focused) == true } && tabs++ < 8) {
                        harness.key(Key.Tab)
                    }
                    assertTrue(harness.practiceButton(ListeningContrastsCatalog.defaultContrasts.first()).config[SemanticsProperties.Focused])
                    harness.key(Key.Enter)
                    assertEquals("i_vs_I", harness.component.selected)
                    harness.scrollTo(20)
                    harness.assertReadableRows()
                    assertTrue(harness.practiceButtons().any { it.config[SemanticsActions.OnClick].label == "Practice Wet / Vet" })
                    assertFalse(harness.hasText("All 20 contrasts"))
                    assertFalse(harness.hasText("Show fewer"))
                    harness.capture("bottom")
                    harness.scrollTo(0)
                    harness.assertReadableRows()
                }
            }
        }
    }

    private data class Configuration(val name: String, val width: Int, val height: Int, val fontScale: Float = 1f)

    private class Harness(
        private val configuration: Configuration,
        private val dark: Boolean = false,
        contrasts: List<ListeningContrast> = ListeningContrastsCatalog.defaultContrasts,
        restoredValues: Map<String, List<Any?>>? = null
    ) : AutoCloseable {
        val component = PreviewComponent(contrasts)
        val registry = SaveableStateRegistry(restoredValues) { true }
        private var frame = 0L
        private val scene = ImageComposeScene(
            width = configuration.width,
            height = configuration.height,
            density = Density(1f, configuration.fontScale)
        ) {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                MinimalPairsTheme(darkTheme = dark) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        DashboardScreen(component, Modifier.widthIn(max = 720.dp).fillMaxSize())
                    }
                }
            }
        }

        fun settle() {
            repeat(20) { scene.render(++frame * 16_666_667L).close() }
        }

        fun nodes(): List<SemanticsNode> {
            fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
            return scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
        }

        fun practiceButtons() = nodes().filter {
            it.config.getOrNull(SemanticsActions.OnClick)?.label?.startsWith("Practice ") == true
        }

        fun practiceButton(contrast: ListeningContrast) = practiceButtons().first {
            it.config[SemanticsActions.OnClick].label == practiceLabel(contrast)
        }

        fun highlightedLabels() = practiceButtons().filter {
            it.config.getOrNull(SemanticsProperties.Selected) == true
        }.map { it.config[SemanticsActions.OnClick].label }

        fun hasText(label: String) = nodes().any { it.text() == label }

        fun scrollTo(index: Int) {
            val list = nodes().first { it.config.contains(SemanticsActions.ScrollToIndex) }
            assertTrue(list.config[SemanticsActions.ScrollToIndex].action!!.invoke(index))
            settle()
        }

        fun key(key: Key) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            settle()
        }

        fun assertReadableRows() {
            for (row in practiceButtons()) {
                assertEquals(Role.Button, row.config[SemanticsProperties.Role])
                assertTrue(row.size.height >= 56, "Rows must preserve their touch targets")
                assertTrue(row.boundsInRoot.left >= 0 && row.boundsInRoot.right <= configuration.width)
            }
            val layouts = mutableListOf<TextLayoutResult>()
            nodes().forEach { it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) }
            assertTrue(layouts.isNotEmpty())
            layouts.forEach { layout ->
                for (line in 0 until layout.lineCount) {
                    val message = "${configuration.name}: ${layout.layoutInput.text}"
                    assertFalse(layout.isLineEllipsized(line), message)
                    assertTrue(layout.getLineBottom(line) <= layout.size.height + 1f, message)
                    assertTrue(layout.getLineRight(line) <= layout.size.width + 1f, message)
                }
            }
        }

        fun capture(state: String) {
            val directory = File("build/reports/listen-home").apply { mkdirs() }
            scene.render(++frame * 16_666_667L).use { image ->
                image.encodeToData()!!.use { data ->
                    File(directory, "${configuration.name}-${if (dark) "dark" else "light"}-$state.png").writeBytes(data.bytes)
                }
            }
        }

        override fun close() = scene.close()
    }

    private class PreviewComponent(override val contrasts: List<ListeningContrast>) : DashboardComponent {
        var selected: String? = null
        override fun onSelectContrast(contrastId: String) { selected = contrastId }
    }

    companion object {
        private fun practiceLabel(contrast: ListeningContrast): String {
            val pair = contrast.pairs.first()
            return "Practice ${pair.word1.replaceFirstChar(Char::uppercaseChar)} / ${pair.word2.replaceFirstChar(Char::uppercaseChar)}"
        }

        private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()

        private fun onUiThread(block: () -> Unit) {
            var failure: Throwable? = null
            SwingUtilities.invokeAndWait {
                try { block() } catch (error: Throwable) { failure = error }
            }
            failure?.let { throw it }
        }
    }
}

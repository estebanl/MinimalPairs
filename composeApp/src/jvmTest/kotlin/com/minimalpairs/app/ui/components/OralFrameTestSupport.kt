package com.minimalpairs.app.ui.components

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.ExperimentalComposeUiApi

@OptIn(ExperimentalComposeUiApi::class)
/** Waits until every 3D mouth view on screen shows a frame for its current mesh and view. */
fun ImageComposeScene.awaitOralFrame(timeoutMillis: Long = 5_000) {
    val deadline = System.nanoTime() + timeoutMillis * 1_000_000
    fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    while (System.nanoTime() < deadline) {
        render().close()
        val flags = semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
            .mapNotNull { it.config.getOrNull(OralFrameCurrent) }
        if (flags.all { it }) return
        Thread.sleep(5)
    }
    error("3D mouth frame was not rendered in time")
}

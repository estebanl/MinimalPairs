package com.minimalpairs.app.sdd

import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import com.minimalpairs.app.ui.reference.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import minimalpairs.composeapp.generated.resources.Res
import java.security.MessageDigest
import kotlin.test.*

// Packaged-resource integration tests use the desktop resource reader. Android host tests
// have no Android runtime or asset manager; geometry-only checks remain in commonTest.
class OralCavityResourceTest {
    @Test
    fun packagedProvenanceMatchesEveryMeshAndIncludesDerivedRecipes() = runTest {
        val manifest = Json.parseToJsonElement(Res.readBytes("files/vtl/manifest.json").decodeToString()).jsonObject
        val files = manifest.getValue("files").jsonObject
        assertEquals(19, files.size)
        files.forEach { (name, entry) ->
            val bytes = Res.readBytes("files/vtl/$name")
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(entry.jsonObject.getValue("sha256").jsonPrimitive.content, hash, name)
        }
        val recipes = manifest.getValue("derived_teaching_poses").jsonObject
        assertEquals(setOf("derived-trap", "derived-palm", "derived-strut", "derived-lot"), recipes.keys)
        recipes.values.forEach { recipe ->
            val parameters = recipe.jsonObject.getValue("parameters").jsonObject
            assertEquals(19, parameters.size)
            assertTrue(parameters.values.all { it.jsonPrimitive.content.toDouble().isFinite() })
        }
        // Anchor an original imported mesh so recipe changes cannot silently replace it.
        assertEquals("7c88a4bd8022ddf8f30e8dc8b81effe9989fe187b405270e661a3a915f6a7040",
            files.getValue("fleece.vtlm").jsonObject.getValue("sha256").jsonPrimitive.content)
    }

    @Test
    fun allCatalogMeshesAreDistinctAndDerivedPosesAreExplicit() = runTest {
        val derived = setOf("æ", "ɑː", "ʌ", "ɒ")
        val catalog = VowelContrastsCatalog.defaultContrasts.flatMap { listOf(it.phoneme1, it.phoneme2) }.toSet()
        assertEquals(catalog, VocalTractLabMeshes.sourceShapes.keys)
        assertEquals(derived, catalog.filter { VocalTractLabMeshes.isDerived(it) }.toSet())
        derived.forEach { assertTrue(VocalTractLabMeshes.poseDescription(it).startsWith("Illustrative")) }
        assertNull(VocalTractLabMeshes.load("unknown"))
        val loaded = VocalTractLabMeshes.sourceShapes.keys.map { VocalTractLabMeshes.load(it)!! }
        assertEquals(10, loaded.toSet().size, "Each original or derived pose must be distinct")
        for (mesh in loaded) {
            assertEquals(2804, mesh.size)
            assertEquals(OralPart.entries.toSet(), mesh.map { it.part }.toSet())
            for (left in listOf(false, true)) {
                val cut = OralCavityModel.cutaway(mesh, true, left)
                assertTrue(cut.isNotEmpty())
                for (face in cut) for (point in listOf(face.a, face.b, face.c)) {
                    assertTrue(if (left) point.x >= -.00001f else point.x <= .00001f)
                    for (yaw in listOf(-100f, 0f, 65f, 90f, 100f)) {
                        val view = OralView(yaw, 40f, 1.3f)
                        val projected = view.project(view.rotate(point))
                        assertTrue(projected.x.isFinite() && projected.y.isFinite() && projected.z.isFinite())
                    }
                }
            }
        }
    }

    @Test
    fun consonantResourcesCoverSharedGesturesAndPhasesWithoutDuplicatingVoicing() = runTest {
        val manifest = Json.parseToJsonElement(Res.readBytes("files/vtl/manifest.json").decodeToString()).jsonObject
        val gestures = manifest.getValue("consonant_teaching_poses").jsonObject
        assertEquals(9, gestures.size)
        gestures.values.forEach { entry ->
            assertTrue(entry.jsonObject.getValue("source_preset").jsonPrimitive.content.endsWith("(a)"))
            assertEquals(19, entry.jsonObject.getValue("parameters").jsonObject.size)
        }
        val distinct = mutableSetOf<List<OralTriangle>>()
        for ((sound, reference) in ConsonantModelReferences.byPhoneme) {
            for (index in reference.phases.indices) {
                val mesh = VocalTractLabMeshes.loadConsonant(sound, index)!!
                assertEquals(2804, mesh.size, "$sound phase $index")
                assertEquals(OralPart.entries.toSet(), mesh.map { it.part }.toSet())
                assertTrue(mesh.flatMap { listOf(it.a, it.b, it.c) }.all {
                    it.x.isFinite() && it.y.isFinite() && it.z.isFinite()
                })
                for (left in listOf(true, false)) assertTrue(OralCavityModel.cutaway(mesh, true, left).isNotEmpty())
                distinct += mesh
            }
        }
        assertEquals(10, distinct.size, "Nine new gestures plus the reused rounded vowel")
        for ((a, b) in listOf("p" to "b", "t" to "d", "k" to "ɡ", "f" to "v", "θ" to "ð", "s" to "z", "ʃ" to "ʒ")) {
            assertSame(VocalTractLabMeshes.loadConsonant(a), VocalTractLabMeshes.loadConsonant(b))
        }
        assertSame(VocalTractLabMeshes.loadConsonant("w"), VocalTractLabMeshes.load("uː"))
        assertSame(VocalTractLabMeshes.loadConsonant("tʃ", 1), VocalTractLabMeshes.loadConsonant("ʃ"))
        assertNotEquals(VocalTractLabMeshes.loadConsonant("tʃ", 0), VocalTractLabMeshes.loadConsonant("tʃ", 1))
        assertNull(VocalTractLabMeshes.loadConsonant("unknown"))
        assertNull(VocalTractLabMeshes.loadConsonant("r"))
        assertNull(VocalTractLabMeshes.loadConsonant("p", 1))
    }

    @Test
    fun malformedMeshesAreRejectedBeforeRendering() = runTest {
        val valid = Res.readBytes("files/vtl/fleece.vtlm")
        fun changedInt(offset: Int, value: Int): ByteArray = valid.copyOf().apply {
            repeat(4) { this[offset + it] = (value ushr (8 * it)).toByte() }
        }
        assertFailsWith<IllegalArgumentException> { VocalTractLabMeshes.decode(valid.copyOf(20)) }
        assertFailsWith<IllegalArgumentException> { VocalTractLabMeshes.decode(changedInt(0, 0)) }
        assertFailsWith<IllegalArgumentException> { VocalTractLabMeshes.decode(changedInt(4, 2)) }
        assertFailsWith<IllegalArgumentException> { VocalTractLabMeshes.decode(changedInt(16, Int.MAX_VALUE)) }
        assertFailsWith<IllegalArgumentException> { VocalTractLabMeshes.decode(changedInt(24, Float.NaN.toBits())) }
        val vertexCount = (0..3).fold(0) { n, i -> n or ((valid[16 + i].toInt() and 255) shl (8 * i)) }
        assertFailsWith<IllegalArgumentException> { VocalTractLabMeshes.decode(changedInt(24 + vertexCount * 12, vertexCount)) }
        assertFailsWith<IllegalArgumentException> { VocalTractLabMeshes.decode(valid + byteArrayOf(0)) }
    }

}

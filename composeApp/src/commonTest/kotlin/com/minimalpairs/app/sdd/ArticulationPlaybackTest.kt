package com.minimalpairs.app.sdd

import com.minimalpairs.app.ui.reference.*
import kotlin.test.*

class ArticulationPlaybackTest {
    private fun spec(review: ArticulationReviewStatus = ArticulationReviewStatus.ACCEPTED) = ArticulationSpec(
        "atlas-illustrative-v1", "p", listOf(ArticulationPhase("closure", "files/anatomy/p-closure.vtlm", 200),
            ArticulationPhase("release", "files/anatomy/p-release.vtlm", 0)), "exporter-topology-hash", review)

    private fun bytes(shift: Float = 0f, indices: List<Int> = listOf(0, 1, 2), material: Int = 1, normal: Float = 1f): ByteArray {
        val result = mutableListOf<Byte>()
        fun int(value: Int) { repeat(4) { result += (value ushr (it * 8)).toByte() } }
        fun float(value: Float) = int(value.toBits())
        result += "VTLM".encodeToByteArray().toList()
        int(1); int(1); int(material); int(3); int(1)
        listOf(0f to 0f, 1f to 0f, 0f to 1f).forEach { (x, y) -> float(x + shift); float(y); float(0f) }
        indices.forEach(::int)
        repeat(3) { float(0f); float(0f); float(normal) }
        return result.toByteArray()
    }
    private fun clip(review: ArticulationReviewStatus = ArticulationReviewStatus.ACCEPTED) =
        ArticulationClip.decode(spec(review), listOf(bytes(), bytes(2f)))

    @Test fun identicalTopologyInterpolatesCoordinatesWithoutRescaling() {
        val clip = clip()
        val start = clip.sample(0).single()
        val end = clip.sample(200).single()
        val middle = clip.sample(100).single()
        assertEquals((start.a.z + end.a.z) / 2, middle.a.z, .00001f)
        assertEquals(start.a.x, middle.a.x)
        assertEquals(1f, middle.na.x, .00001f)
        assertEquals(clip.sample(0), clip.sample(-20))
        assertEquals(clip.sample(200), clip.sample(500))
    }
    @Test fun topologyChangeRejectedEvenWithIdenticalManifestHash() {
        assertFailsWith<IllegalArgumentException> { ArticulationClip.decode(spec(), listOf(bytes(), bytes(indices=listOf(0, 2, 1)))) }
        assertFailsWith<IllegalArgumentException> { ArticulationClip.decode(spec(), listOf(bytes(), bytes(material=2))) }
        assertFailsWith<IllegalArgumentException> { ArticulationClip.decode(spec(), listOf(bytes())) }
    }
    @Test fun malformedAssetRejectedBeforeTopologyInspection() {
        assertFailsWith<IllegalArgumentException> { ArticulationClip.decode(spec(), listOf(bytes(), bytes().copyOf(20))) }
        assertFailsWith<IllegalArgumentException> { ArticulationClip.decode(spec(), listOf(bytes(), bytes(Float.NaN))) }
    }
    @Test fun oppositeNormalsNeverCreateZeroOrNonfiniteNormal() {
        val clip = ArticulationClip.decode(spec(), listOf(bytes(normal=1f), bytes(normal=-1f)))
        assertEquals(-1f, clip.sample(100).single().na.x)
    }
    @Test fun staleLoadAndTickIgnoredWhenSoundChanges() {
        val p = ArticulationPlaybackState().select("p")
        val newer = p.select("t")
        assertEquals(newer, newer.loaded(p.generation, clip()))
        assertEquals(newer, newer.loadFailed(p.generation))
        val playing = p.loaded(p.generation, clip()).play()
        val changed = playing.select("t")
        assertNull(changed.clip)
        assertFalse(changed.playing)
        assertEquals(changed, changed.advance(playing.generation, 100))
    }
    @Test fun pauseResetAndReplayInvalidateOldPlaybackTicks() {
        val selected = ArticulationPlaybackState().select("p")
        val playing = selected.loaded(selected.generation, clip()).play()
        assertEquals(playing, playing.play())
        val paused = playing.advance(playing.generation, 75).pause()
        assertEquals(75, paused.elapsedMs)
        assertEquals(paused, paused.advance(playing.generation, 50))
        val reset = paused.reset().play()
        assertEquals(0, reset.elapsedMs)
        assertEquals(reset, reset.advance(playing.generation, 100))
        val ended = reset.advance(reset.generation, Int.MAX_VALUE)
        assertFalse(ended.playing)
        assertEquals(200, ended.elapsedMs)
        assertEquals(0, ended.play().elapsedMs)
    }
    @Test fun failedOrExperimentalLoadRetainsStaticFallback() {
        val selected = ArticulationPlaybackState().select("p")
        assertNull(selected.loaded(selected.generation, clip(ArticulationReviewStatus.EXPERIMENTAL)).clip)
        assertFalse(selected.play().playing)
        assertTrue(selected.loadFailed(selected.generation).failed)
        assertNull(ReviewedArticulations.forPhoneme("p"))
        assertEquals(selected, selected.loaded(selected.generation, ArticulationClip.decode(spec().copy(phoneme="t"), listOf(bytes(), bytes()))))
    }
    @Test fun backgroundPauseDuringLoadingDoesNotInvalidateLoad() {
        val selected = ArticulationPlaybackState().select("p")
        val paused = selected.pause()
        assertNotNull(paused.loaded(selected.generation, clip()).clip)
        assertFalse(paused.loaded(selected.generation, clip()).playing)
    }
    @Test fun clippingSideChangesFrameIdentityEvenWithinSamePlayback() {
        val left = articulationFrameKey("p", "atlas-illustrative-v1", 1, true, true)
        assertNotEquals(left, articulationFrameKey("p", "atlas-illustrative-v1", 1, true, false))
        assertNotEquals(left, articulationFrameKey("p", "atlas-illustrative-v1", 2, true, true))
        assertNotEquals(left, articulationFrameKey("t", "atlas-illustrative-v1", 1, true, true))
    }
    @Test fun degenerateEndpointsRejected() {
        assertFailsWith<IllegalArgumentException> {
            ArticulationClip.decode(spec(), listOf(bytes(indices=listOf(0, 0, 2)), bytes(indices=listOf(0, 0, 2))))
        }
    }
    @Test fun zeroNormalsRejected() {
        assertFailsWith<IllegalArgumentException> { ArticulationClip.decode(spec(), listOf(bytes(normal=0f), bytes())) }
    }
    @Test fun phaseBoundariesAndSingleSnapshotStayExact() {
        val triple = spec().copy(phases=listOf(
            ArticulationPhase("onset", "files/anatomy/a.vtlm", 100),
            ArticulationPhase("middle", "files/anatomy/b.vtlm", 200),
            ArticulationPhase("target", "files/anatomy/c.vtlm", 0)))
        val animated = ArticulationClip.decode(triple, listOf(bytes(), bytes(2f), bytes(4f)))
        assertEquals(VocalTractLabMeshes.decode(bytes(2f)), animated.sample(100))
        assertEquals(VocalTractLabMeshes.decode(bytes(4f)), animated.sample(300))
        val steady = ArticulationClip.decode(spec().copy(phases=listOf(
            ArticulationPhase("target", "files/anatomy/p-target.vtlm", 0))), listOf(bytes()))
        val selected = ArticulationPlaybackState().select("p")
        assertFalse(selected.loaded(selected.generation, steady).play().playing)
        assertEquals(steady.sample(0), steady.sample(Int.MAX_VALUE))
    }
    @Test fun authoredSectionMetadataIsValidatedAndRetainedAcrossInterpolation() {
        val authored = spec().copy(clipMode=ArticulationClipMode.AUTHORED_SECTIONS, sectionTriangleIndices=listOf(0))
        val clip = ArticulationClip.decode(authored, listOf(bytes(), bytes(2f)))
        assertTrue(clip.sample(100).single().isSection)
        assertFailsWith<IllegalArgumentException> { authored.copy(sectionTriangleIndices=listOf(0,0)) }
        assertFailsWith<IllegalArgumentException> { authored.copy(sectionTriangleIndices=emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            ArticulationClip.decode(authored.copy(sectionTriangleIndices=listOf(1)), listOf(bytes(),bytes()))
        }
        val offPlane = bytes().copyOf()
        // First vertex's lateral component (12-byte header + 12-byte surface header +8).
        val bits = 1f.toBits()
        repeat(4) { offPlane[32+it] = (bits ushr (it*8)).toByte() }
        assertFailsWith<IllegalArgumentException> { ArticulationClip.decode(authored, listOf(bytes(), offPlane)) }
    }
    @Test fun acceptedNearPlaneSectionsAreCanonicalizedForBothViewingSides() {
        val authored = spec().copy(clipMode=ArticulationClipMode.AUTHORED_SECTIONS, sectionTriangleIndices=listOf(0))
        val nearPlane = bytes().copyOf()
        val lateral = (.000001f / .23f).toBits()
        repeat(3) { vertex -> repeat(4) { byte -> nearPlane[32 + vertex * 12 + byte] = (lateral ushr (byte * 8)).toByte() } }
        val mesh = ArticulationClip.decode(authored, listOf(nearPlane, nearPlane)).sample(0)
        assertTrue(mesh.flatMap { listOf(it.a, it.b, it.c) }.all { it.x == 0f })
        for (left in listOf(true, false)) {
            assertEquals(mesh, OralCavityModel.cutaway(mesh, true, left, authoredSections=true))
        }
    }
    @Test fun invalidManifestPathsAndDurationsRejected() {
        assertFailsWith<IllegalArgumentException> { spec().copy(phases=listOf(ArticulationPhase("closure", "files/anatomy/../bad.vtlm", 0))) }
        assertFailsWith<IllegalArgumentException> { spec().copy(phases=listOf(ArticulationPhase("closure", "files/anatomy/x.vtlm", -1))) }
        assertFailsWith<IllegalArgumentException> { spec().copy(phases=spec().phases.map { it.copy(name="same") }) }
    }
}

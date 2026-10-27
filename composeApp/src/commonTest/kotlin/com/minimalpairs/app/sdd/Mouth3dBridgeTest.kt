package com.minimalpairs.app.sdd

import com.minimalpairs.app.ui.reference.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class Mouth3dBridgeTest {
    @Test fun onlyTheApprovedPairHasPoses() {
        assertEquals(MouthPose.sheep, mouthPose("iː", "sheep"))
        assertEquals(MouthPose.ship, mouthPose("ɪ", "ship"))
        for ((ipa, word) in listOf("iː" to "seat", "uː" to "fool", "ɪ" to "sheep", "?" to "unknown")) {
            assertNull(mouthPose(ipa, word))
        }
    }
    @Test fun rapidLoadingSelectionsKeepOnlyLatestAndDoNotReloadOnRepeatedUpdates() {
        val sent = mutableListOf<String>()
        val bridge = Mouth3dBridge(sent::add)
        repeat(100) { bridge.update(Mouth3dState(if (it % 2 == 0) MouthPose.sheep else MouthPose.ship)) }
        assertTrue(sent.isEmpty())
        bridge.onReady()
        assertEquals(MouthPose.ship, Json.decodeFromString<Mouth3dState>(sent.single()).pose)
        bridge.onReady()
        bridge.update(Mouth3dState(MouthPose.ship))
        assertEquals(1, sent.size)
    }
    @Test fun backgroundResumeAndRepeatedNavigationUseIndependentSessions() {
        repeat(10) {
            val sent = mutableListOf<String>()
            val bridge = Mouth3dBridge(sent::add)
            bridge.update(Mouth3dState(MouthPose.sheep, active = true))
            bridge.onReady()
            bridge.update(Mouth3dState(MouthPose.ship, active = false))
            bridge.update(Mouth3dState(MouthPose.ship, active = true, reducedMotion = false))
            assertEquals(listOf(true, false, true), sent.map { Json.decodeFromString<Mouth3dState>(it).active })
            bridge.dispose()
            bridge.dispose()
            bridge.update(Mouth3dState(MouthPose.sheep))
            bridge.onReady()
            assertTrue(bridge.closed)
            assertEquals(4, sent.size)
            assertTrue(sent.last().contains("dispose"))
        }
    }
    @Test fun disposalBeforeReadyNeverFlushesAStalePose() {
        val sent = mutableListOf<String>()
        val bridge = Mouth3dBridge(sent::add)
        bridge.update(Mouth3dState(MouthPose.sheep))
        bridge.dispose()
        bridge.onReady()
        assertEquals(listOf("""{"version":1,"type":"dispose"}"""), sent)
        assertFailsWith<IllegalArgumentException> { Mouth3dState(MouthPose.sheep, opacity = Float.NaN) }
    }
}

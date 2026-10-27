package com.minimalpairs.app.sdd

import com.minimalpairs.app.core.dsp.SpeechAudioValidation
import kotlin.test.*

class SpeechAudioValidationTest {
    @Test
    fun rejectsSilenceDcClicksAndOutOfBoundsDurationsButAcceptsUnvoicedEnergy() {
        for (input in listOf(ShortArray(0), ShortArray(3200), ShortArray(3200) { 2000 },
            ShortArray(100), ShortArray(480001), ShortArray(3200) { if (it == 1600) 20000 else 0 })) {
            assertFalse(SpeechAudioValidation.hasUsableAudio(input))
        }
        assertTrue(SpeechAudioValidation.hasUsableAudio(ShortArray(3200) { if (it % 2 == 0) 2000 else -2000 }))
    }
}

package com.minimalpairs.app.sdd

import com.minimalpairs.app.core.cv.FacialLandmarkAnalyzer
import com.minimalpairs.app.core.cv.LandmarkPoint
import com.minimalpairs.app.core.model.VisualIndicators
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FacialLandmarkAnalyzerTest {

    @Test
    fun testSpreadUnroundedLipApertureCalculation() {
        // High front unrounded /iː/ (spread lips, narrow vertical aperture)
        val landmarks = mapOf(
            FacialLandmarkAnalyzer.IDX_UPPER_LIP to LandmarkPoint(0.5f, 0.60f),
            FacialLandmarkAnalyzer.IDX_LOWER_LIP to LandmarkPoint(0.5f, 0.63f), // vertical diff = 0.03
            FacialLandmarkAnalyzer.IDX_MOUTH_LEFT to LandmarkPoint(0.40f, 0.615f),
            FacialLandmarkAnalyzer.IDX_MOUTH_RIGHT to LandmarkPoint(0.60f, 0.615f), // width = 0.20
            FacialLandmarkAnalyzer.IDX_NOSE_BASE to LandmarkPoint(0.5f, 0.45f),
            FacialLandmarkAnalyzer.IDX_CHIN to LandmarkPoint(0.5f, 0.67f),
            FacialLandmarkAnalyzer.IDX_LEFT_EYE_OUTER to LandmarkPoint(0.35f, 0.35f),
            FacialLandmarkAnalyzer.IDX_RIGHT_EYE_OUTER to LandmarkPoint(0.65f, 0.35f)
        )

        val targetIndicators = VisualIndicators(
            lipApertureRatio = 0.15f,
            jawDropMinMm = 5f,
            jawDropMaxMm = 12f
        )

        val biometrics = FacialLandmarkAnalyzer.analyze(landmarks, targetIndicators)

        // Aperture ratio should be ~0.03 / 0.20 = 0.15
        assertEquals(0.15f, biometrics.lipApertureRatio, 0.01f)
        assertTrue(biometrics.isApertureCompliant)
        assertTrue(biometrics.isJawDropCompliant)
    }

    @Test
    fun testOpenVowelJawDropCalculation() {
        // Open near-front vowel /æ/ (wide jaw drop, larger aperture)
        val landmarks = mapOf(
            FacialLandmarkAnalyzer.IDX_UPPER_LIP to LandmarkPoint(0.5f, 0.58f),
            FacialLandmarkAnalyzer.IDX_LOWER_LIP to LandmarkPoint(0.5f, 0.65f), // vertical diff = 0.07
            FacialLandmarkAnalyzer.IDX_MOUTH_LEFT to LandmarkPoint(0.40f, 0.615f),
            FacialLandmarkAnalyzer.IDX_MOUTH_RIGHT to LandmarkPoint(0.60f, 0.615f), // width = 0.20
            FacialLandmarkAnalyzer.IDX_NOSE_BASE to LandmarkPoint(0.5f, 0.45f),
            FacialLandmarkAnalyzer.IDX_CHIN to LandmarkPoint(0.5f, 0.71f), // larger nose-to-chin distance
            FacialLandmarkAnalyzer.IDX_LEFT_EYE_OUTER to LandmarkPoint(0.35f, 0.35f),
            FacialLandmarkAnalyzer.IDX_RIGHT_EYE_OUTER to LandmarkPoint(0.65f, 0.35f)
        )

        val targetIndicators = VisualIndicators(
            lipApertureRatio = 0.35f,
            jawDropMinMm = 14f,
            jawDropMaxMm = 22f
        )

        val biometrics = FacialLandmarkAnalyzer.analyze(landmarks, targetIndicators)

        // Aperture ratio = 0.07 / 0.20 = 0.35
        assertEquals(0.35f, biometrics.lipApertureRatio, 0.01f)
        assertTrue(biometrics.isApertureCompliant)
        assertTrue(biometrics.isJawDropCompliant)
    }
}

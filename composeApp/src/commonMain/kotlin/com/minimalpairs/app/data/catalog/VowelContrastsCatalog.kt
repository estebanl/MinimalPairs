package com.minimalpairs.app.data.catalog

import com.minimalpairs.app.core.model.MinimalPairWord
import com.minimalpairs.app.core.model.PhoneticBounds
import com.minimalpairs.app.core.model.PhoneticContrast
import com.minimalpairs.app.core.model.VisualIndicators

object VowelContrastsCatalog {

    const val RAW_SPEC_YAML: String = """version: "1.0"

contrast_pairs:
  - id: "i_vs_I"
    target_phonemes: ["iː", "ɪ"]
    description: "Close front unrounded vs. Near-close near-front unrounded"
    examples:
      - pair: ["sheep", "ship"]
      - pair: ["feet", "fit"]
      - pair: ["seat", "sit"]
      - pair: ["leak", "lick"]
      - pair: ["cheeks", "chicks"]
    acoustic_bounds:
      phoneme_1: # /iː/
        f1_range_hz: [250, 380]
        f2_range_hz: [2100, 2600]
        min_duration_ms: 120
      phoneme_2: # /ɪ/
        f1_range_hz: [380, 520]
        f2_range_hz: [1700, 2100]
        max_duration_ms: 100
    visual_indicators:
      lip_aperture_ratio: 0.15
      jaw_drop_mm: [5, 12]

  - id: "ae_vs_e"
    target_phonemes: ["æ", "e"]
    description: "Near-open front unrounded vs. Open-mid front unrounded"
    examples:
      - pair: ["bad", "bed"]
      - pair: ["man", "men"]
      - pair: ["pan", "pen"]
      - pair: ["sad", "said"]
      - pair: ["cattle", "kettle"]
    acoustic_bounds:
      phoneme_1: # /æ/
        f1_range_hz: [660, 860]
        f2_range_hz: [1500, 1900]
        min_duration_ms: 140
      phoneme_2: # /e/
        f1_range_hz: [500, 660]
        f2_range_hz: [1700, 2100]
        max_duration_ms: 110
    visual_indicators:
      lip_aperture_ratio: 0.35
      jaw_drop_mm: [14, 22]

  - id: "a_vs_uh"
    target_phonemes: ["ɑː", "ʌ"]
    description: "Open back unrounded vs. Open-mid back unrounded"
    examples:
      - pair: ["heart", "hut"]
      - pair: ["calm", "come"]
      - pair: ["dark", "duck"]
      - pair: ["march", "much"]
      - pair: ["barn", "bun"]
    acoustic_bounds:
      phoneme_1: # /ɑː/
        f1_range_hz: [680, 850]
        f2_range_hz: [1050, 1300]
        min_duration_ms: 150
      phoneme_2: # /ʌ/
        f1_range_hz: [580, 720]
        f2_range_hz: [1250, 1500]
        max_duration_ms: 100
    visual_indicators:
      lip_aperture_ratio: 0.40
      jaw_drop_mm: [16, 24]

  - id: "u_vs_uu"
    target_phonemes: ["ʊ", "uː"]
    description: "Near-close near-back rounded vs. Close back rounded"
    examples:
      - pair: ["full", "fool"]
      - pair: ["pull", "pool"]
      - pair: ["look", "luke"]
      - pair: ["soot", "suit"]
      - pair: ["stood", "stewed"]
    acoustic_bounds:
      phoneme_1: # /ʊ/
        f1_range_hz: [400, 520]
        f2_range_hz: [1200, 1500]
        max_duration_ms: 100
      phoneme_2: # /uː/
        f1_range_hz: [280, 380]
        f2_range_hz: [800, 1200]
        min_duration_ms: 130
    visual_indicators:
      lip_aperture_ratio: 0.12
      jaw_drop_mm: [6, 12]

  - id: "o_vs_or"
    target_phonemes: ["ɒ", "ɔː"]
    description: "Open back rounded vs. Open-mid back rounded"
    examples:
      - pair: ["cot", "caught"]
      - pair: ["shot", "short"]
      - pair: ["pot", "port"]
      - pair: ["spot", "sport"]
      - pair: ["fox", "forks"]
    acoustic_bounds:
      phoneme_1: # /ɒ/
        f1_range_hz: [550, 720]
        f2_range_hz: [900, 1200]
        max_duration_ms: 110
      phoneme_2: # /ɔː/
        f1_range_hz: [400, 560]
        f2_range_hz: [750, 1050]
        min_duration_ms: 140
    visual_indicators:
      lip_aperture_ratio: 0.28
      jaw_drop_mm: [12, 18]

  - id: "e_vs_I"
    target_phonemes: ["e", "ɪ"]
    description: "Open-mid front unrounded vs. Near-close near-front unrounded"
    examples:
      - pair: ["pen", "pin"]
      - pair: ["ten", "tin"]
      - pair: ["bet", "bit"]
      - pair: ["set", "sit"]
      - pair: ["dead", "did"]
    acoustic_bounds:
      phoneme_1: # /e/
        f1_range_hz: [500, 660]
        f2_range_hz: [1700, 2100]
        min_duration_ms: 110
      phoneme_2: # /ɪ/
        f1_range_hz: [380, 520]
        f2_range_hz: [1700, 2100]
        max_duration_ms: 100
    visual_indicators:
      lip_aperture_ratio: 0.25
      jaw_drop_mm: [10, 16]

  - id: "ae_vs_uh"
    target_phonemes: ["æ", "ʌ"]
    description: "Near-open front unrounded vs. Open-mid central unrounded"
    examples:
      - pair: ["cat", "cut"]
      - pair: ["hat", "hut"]
      - pair: ["match", "much"]
      - pair: ["ankle", "uncle"]
    acoustic_bounds:
      phoneme_1: # /æ/
        f1_range_hz: [660, 860]
        f2_range_hz: [1500, 1900]
        min_duration_ms: 140
      phoneme_2: # /ʌ/
        f1_range_hz: [580, 720]
        f2_range_hz: [1250, 1500]
        max_duration_ms: 100
    visual_indicators:
      lip_aperture_ratio: 0.35
      jaw_drop_mm: [14, 22]
"""

    fun parseYaml(yamlString: String = RAW_SPEC_YAML): List<PhoneticContrast> {
        val lines = yamlString.lines()
        val contrasts = mutableListOf<PhoneticContrast>()

        var currentId = ""
        var currentP1 = ""
        var currentP2 = ""
        var currentDesc = ""
        val currentPairs = mutableListOf<MinimalPairWord>()

        var f1_1_min = 250f
        var f1_1_max = 380f
        var f2_1_min = 2100f
        var f2_1_max = 2600f
        var minDur1: Int? = 120
        var maxDur1: Int? = null

        var f1_2_min = 380f
        var f1_2_max = 520f
        var f2_2_min = 1700f
        var f2_2_max = 2100f
        var minDur2: Int? = null
        var maxDur2: Int? = 100

        var lipAperture = 0.15f
        var jawDropMin = 5f
        var jawDropMax = 12f

        var parsingPhoneme1 = false
        var parsingPhoneme2 = false

        fun flushContrast() {
            if (currentId.isNotBlank() && currentP1.isNotBlank() && currentP2.isNotBlank()) {
                contrasts.add(
                    PhoneticContrast(
                        id = currentId,
                        phoneme1 = currentP1,
                        phoneme2 = currentP2,
                        description = currentDesc,
                        pairs = currentPairs.toList(),
                        boundsPhoneme1 = PhoneticBounds(f1_1_min, f1_1_max, f2_1_min, f2_1_max, minDur1, maxDur1),
                        boundsPhoneme2 = PhoneticBounds(f1_2_min, f1_2_max, f2_2_min, f2_2_max, minDur2, maxDur2),
                        visualIndicators = VisualIndicators(lipAperture, jawDropMin, jawDropMax)
                    )
                )
            }
            currentPairs.clear()
            minDur1 = null
            maxDur1 = null
            minDur2 = null
            maxDur2 = null
            parsingPhoneme1 = false
            parsingPhoneme2 = false
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.startsWith("- id:")) {
                flushContrast()
                currentId = line.substringAfter("- id:").replace("\"", "").trim()
            } else if (line.startsWith("target_phonemes:")) {
                val arrayStr = line.substringAfter("target_phonemes:").trim()
                val items = arrayStr.removeSurrounding("[", "]").split(",")
                    .map { it.replace("\"", "").trim() }
                if (items.size >= 2) {
                    currentP1 = items[0]
                    currentP2 = items[1]
                }
            } else if (line.startsWith("description:")) {
                currentDesc = line.substringAfter("description:").replace("\"", "").trim()
            } else if (line.startsWith("- pair:")) {
                val arrayStr = line.substringAfter("- pair:").trim()
                val words = arrayStr.removeSurrounding("[", "]").split(",")
                    .map { it.replace("\"", "").trim() }
                if (words.size >= 2) {
                    currentPairs.add(MinimalPairWord(words[0], words[1]))
                }
            } else if (line.startsWith("phoneme_1:")) {
                parsingPhoneme1 = true
                parsingPhoneme2 = false
            } else if (line.startsWith("phoneme_2:")) {
                parsingPhoneme1 = false
                parsingPhoneme2 = true
            } else if (line.startsWith("f1_range_hz:")) {
                val parts = line.substringAfter("f1_range_hz:").trim().removeSurrounding("[", "]").split(",")
                if (parts.size >= 2) {
                    val low = parts[0].trim().toFloat()
                    val high = parts[1].trim().toFloat()
                    if (parsingPhoneme1) { f1_1_min = low; f1_1_max = high }
                    if (parsingPhoneme2) { f1_2_min = low; f1_2_max = high }
                }
            } else if (line.startsWith("f2_range_hz:")) {
                val parts = line.substringAfter("f2_range_hz:").trim().removeSurrounding("[", "]").split(",")
                if (parts.size >= 2) {
                    val low = parts[0].trim().toFloat()
                    val high = parts[1].trim().toFloat()
                    if (parsingPhoneme1) { f2_1_min = low; f2_1_max = high }
                    if (parsingPhoneme2) { f2_2_min = low; f2_2_max = high }
                }
            } else if (line.startsWith("min_duration_ms:")) {
                val ms = line.substringAfter("min_duration_ms:").trim().toInt()
                if (parsingPhoneme1) minDur1 = ms
                if (parsingPhoneme2) minDur2 = ms
            } else if (line.startsWith("max_duration_ms:")) {
                val ms = line.substringAfter("max_duration_ms:").trim().toInt()
                if (parsingPhoneme1) maxDur1 = ms
                if (parsingPhoneme2) maxDur2 = ms
            } else if (line.startsWith("lip_aperture_ratio:")) {
                lipAperture = line.substringAfter("lip_aperture_ratio:").trim().toFloat()
            } else if (line.startsWith("jaw_drop_mm:")) {
                val parts = line.substringAfter("jaw_drop_mm:").trim().removeSurrounding("[", "]").split(",")
                if (parts.size >= 2) {
                    jawDropMin = parts[0].trim().toFloat()
                    jawDropMax = parts[1].trim().toFloat()
                }
            }
        }
        flushContrast()

        return contrasts
    }

    val defaultContrasts: List<PhoneticContrast> by lazy {
        parseYaml(RAW_SPEC_YAML)
    }

    fun getContrastById(id: String): PhoneticContrast? {
        return defaultContrasts.firstOrNull { it.id == id }
    }
}

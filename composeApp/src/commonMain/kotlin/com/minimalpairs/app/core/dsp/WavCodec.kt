package com.minimalpairs.app.core.dsp

/**
 * Encodes and decodes 16kHz 16-bit signed integer mono PCM WAV files.
 */
object WavCodec {
    const val SAMPLE_RATE = 16000
    const val BITS_PER_SAMPLE = 16
    const val CHANNELS = 1

    fun encodePcmToWav(samples: ShortArray): ByteArray {
        val byteDataSize = samples.size * 2
        val totalSize = 36 + byteDataSize
        val header = ByteArray(44)

        // "RIFF"
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        // Overall size (totalSize)
        header[4] = (totalSize and 0xff).toByte()
        header[5] = ((totalSize shr 8) and 0xff).toByte()
        header[6] = ((totalSize shr 16) and 0xff).toByte()
        header[7] = ((totalSize shr 24) and 0xff).toByte()

        // "WAVE"
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // "fmt "
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        // Subchunk1Size (16 for PCM)
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0

        // AudioFormat (1 for PCM)
        header[20] = 1
        header[21] = 0

        // NumChannels (1)
        header[22] = CHANNELS.toByte()
        header[23] = 0

        // SampleRate (16000)
        header[24] = (SAMPLE_RATE and 0xff).toByte()
        header[25] = ((SAMPLE_RATE shr 8) and 0xff).toByte()
        header[26] = ((SAMPLE_RATE shr 16) and 0xff).toByte()
        header[27] = ((SAMPLE_RATE shr 24) and 0xff).toByte()

        // ByteRate = SampleRate * NumChannels * BitsPerSample/8
        val byteRate = SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8)
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        // BlockAlign = NumChannels * BitsPerSample/8
        val blockAlign = CHANNELS * (BITS_PER_SAMPLE / 8)
        header[32] = blockAlign.toByte()
        header[33] = 0

        // BitsPerSample
        header[34] = BITS_PER_SAMPLE.toByte()
        header[35] = 0

        // "data"
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        // Subchunk2Size (byteDataSize)
        header[40] = (byteDataSize and 0xff).toByte()
        header[41] = ((byteDataSize shr 8) and 0xff).toByte()
        header[42] = ((byteDataSize shr 16) and 0xff).toByte()
        header[43] = ((byteDataSize shr 24) and 0xff).toByte()

        val wavBytes = ByteArray(44 + byteDataSize)
        header.copyInto(wavBytes, 0, 0, 44)

        var byteIdx = 44
        for (i in samples.indices) {
            val sample = samples[i].toInt()
            wavBytes[byteIdx++] = (sample and 0xff).toByte()
            wavBytes[byteIdx++] = ((sample shr 8) and 0xff).toByte()
        }

        return wavBytes
    }

    fun decodeWavToPcm(wavBytes: ByteArray): ShortArray {
        if (wavBytes.size < 44) return ShortArray(0)
        // Find "data" chunk
        var offset = 12
        while (offset + 8 <= wavBytes.size) {
            val chunkId = wavBytes.decodeToString(offset, offset + 4)
            val chunkSize = (wavBytes[offset + 4].toInt() and 0xff) or
                    ((wavBytes[offset + 5].toInt() and 0xff) shl 8) or
                    ((wavBytes[offset + 6].toInt() and 0xff) shl 16) or
                    ((wavBytes[offset + 7].toInt() and 0xff) shl 24)

            if (chunkId == "data") {
                val dataStart = offset + 8
                val availableBytes = (wavBytes.size - dataStart).coerceAtMost(chunkSize)
                val sampleCount = availableBytes / 2
                val result = ShortArray(sampleCount)
                for (i in 0 until sampleCount) {
                    val low = wavBytes[dataStart + i * 2].toInt() and 0xff
                    val high = wavBytes[dataStart + i * 2 + 1].toInt() shl 8
                    result[i] = (low or high).toShort()
                }
                return result
            }
            offset += 8 + chunkSize
        }
        return ShortArray(0)
    }
}

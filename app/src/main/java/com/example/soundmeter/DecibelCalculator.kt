package com.example.soundmeter

import kotlin.math.abs
import kotlin.math.log10

/**
 * Utility class to calculate decibel values from raw audio data
 */
object DecibelCalculator {

    // Reference amplitude for 0 dB (adjusted based on device calibration)
    // Note: This is an approximation and might need to be calibrated per device for accuracy
    private const val REFERENCE_AMPLITUDE = 0.00002 // Approximate reference value

    /**
     * Calculate the RMS (Root Mean Square) value from a PCM audio buffer
     * @param buffer The raw audio data buffer (16-bit PCM samples)
     * @return The RMS value
     */
    fun calculateRMS(buffer: ShortArray): Double {
        var sum = 0.0

        // Sum up the squares of all samples
        for (sample in buffer) {
            sum += sample * sample
        }

        // Calculate the mean
        val mean = sum / buffer.size

        // Return the square root of the mean
        return Math.sqrt(mean)
    }

    /**
     * Convert RMS amplitude to decibel value
     * @param rms The RMS amplitude value
     * @return The decibel value
     */
    fun rmsToDecibel(rms: Double): Double {
        // Avoid log of 0 or negative values
        if (rms <= 0) return 0.0

        // dB = 20 * log10(amplitude / reference)
        return 20 * log10(rms / REFERENCE_AMPLITUDE)
    }

    /**
     * Calculate the decibel value directly from a raw audio buffer
     * @param buffer The raw audio data buffer (16-bit PCM samples)
     * @return The decibel value
     */
    fun calculateDecibel(buffer: ShortArray): Double {
        val rms = calculateRMS(buffer)
        return rmsToDecibel(rms)
    }

    /**
     * Alternative method: Calculate the decibel using maximum amplitude
     * This is less accurate than RMS but can be useful in some cases
     * @param buffer The raw audio data buffer
     * @return The decibel value based on maximum amplitude
     */
    fun calculateDecibelFromMaxAmplitude(buffer: ShortArray): Double {
        var maxAmplitude = 0

        // Find the maximum absolute amplitude in the buffer
        for (sample in buffer) {
            val absValue = abs(sample.toInt())
            if (absValue > maxAmplitude) {
                maxAmplitude = absValue
            }
        }

        // Convert to double for calculation
        val maxAmplitudeDouble = maxAmplitude.toDouble()

        // Normalize based on max value for 16-bit audio (32767)
        val normalizedValue = maxAmplitudeDouble / 32767.0

        // Avoid log of 0
        if (normalizedValue <= 0) return 0.0

        // Calculate dB using the formula: dB = 20 * log10(amplitude)
        return 20 * log10(normalizedValue)
    }
}
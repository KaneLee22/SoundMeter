package com.example.soundmeter

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Class responsible for audio recording and decibel measurement
 */
class AudioRecorder {

    // Audio configuration constants
    companion object {
        private const val SAMPLE_RATE = 44100 // Hz (CD quality)
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2 // Multiplier for minimum buffer size
    }

    // The minimum buffer size required for the AudioRecord object
    private val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    )

    // The actual buffer size we'll use (larger for better performance)
    private val bufferSize = minBufferSize * BUFFER_SIZE_FACTOR

    // Buffer to hold audio data
    private val audioBuffer = ShortArray(bufferSize)

    // AudioRecord instance reference
    private var audioRecord: AudioRecord? = null

    // Recording state
    private var isRecording = false

    /**
     * Start the audio recording process
     * @return true if recording started successfully, false otherwise
     */
    fun start(): Boolean {
        // If already recording, return true
        if (isRecording) return true

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            // Check if AudioRecord initialized correctly
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                return false
            }

            audioRecord?.startRecording()
            isRecording = true
            return true
        } catch (e: Exception) {
            // Log error or handle exception
            e.printStackTrace()
            stop()
            return false
        }
    }

    /**
     * Stop recording and release resources
     */
    fun stop() {
        isRecording = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Read audio data and return a Flow that emits decibel values
     * @param intervalMs Time between measurements in milliseconds
     * @return Flow of decibel values
     */
    fun startDecibelMeter(intervalMs: Long = 100): Flow<Double> = flow {
        if (!isRecording) {
            if (!start()) {
                throw IllegalStateException("Failed to start audio recording")
            }
        }

        while (isRecording) {
            val decibelValue = readDecibelValue()
            emit(decibelValue)

            // Wait for specified interval
            kotlinx.coroutines.delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Single decibel reading (not continuous)
     * @return Current decibel value
     */
    suspend fun readDecibelValue(): Double = withContext(Dispatchers.IO) {
        if (!isRecording || audioRecord == null) {
            return@withContext 0.0
        }

        // Read audio data into buffer
        val readSize = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0

        // If read was successful, calculate decibel value
        if (readSize > 0) {
            return@withContext DecibelCalculator.calculateDecibel(audioBuffer)
        }

        return@withContext 0.0
    }

    /**
     * Check if currently recording
     * @return true if recording, false otherwise
     */
    fun isRecording(): Boolean = isRecording
}
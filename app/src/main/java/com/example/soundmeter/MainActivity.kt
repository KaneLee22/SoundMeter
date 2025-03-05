package com.example.soundmeter

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.soundmeter.ui.theme.Green
import com.example.soundmeter.ui.theme.Red
import com.example.soundmeter.ui.theme.SoundMeterTheme
import com.example.soundmeter.ui.theme.Yellow
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * MainActivity: Main entry point for the Sound Meter application
 * References:
 * - Algorithm for audio processing adapted from: https://github.com/albertopasqualetto/SoundMeterESP
 */
class MainActivity : ComponentActivity() {

    companion object {
        // Audio recording constants
        private const val SAMPLE_RATE = 44100         // CD quality audio (Hz)
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT  // 16-bit samples
        private const val THRESHOLD_DB = 75.0         // Noise threshold in dB

        // Reference amplitude (hearing threshold at 1kHz)
        // Reference: https://github.com/albertopasqualetto/SoundMeterESP
        private const val REFERENCE_AMPLITUDE = 0.00002  // 20 µPa - hearing threshold
    }

    // Audio recording components
    private var audioRecord: AudioRecord? = null
    private var bufferSize = 0
    private val isRecording = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calculate buffer size for audio recording
        // This determines how much audio data we process at once
        bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        // Increase buffer size for better audio quality and stability
        // Using a larger buffer helps prevent audio glitches
        if (bufferSize != AudioRecord.ERROR && bufferSize != AudioRecord.ERROR_BAD_VALUE) {
            bufferSize *= 3
        }

        // Set up the UI using Jetpack Compose
        setContent {
            // Apply our custom theme
            SoundMeterTheme {
                // Display the main sound meter interface
                SoundMeterApp(
                    onStartRecording = { startRecording() },
                    onStopRecording = { stopRecording() },
                    isRecordingState = isRecording
                )
            }
        }
    }

    /**
     * Main Compose UI for the Sound Meter
     *
     * @param onStartRecording Callback for when recording should start
     * @param onStopRecording Callback for when recording should stop
     * @param isRecordingState Atomic boolean tracking recording state
     */
    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    fun SoundMeterApp(
        onStartRecording: () -> Unit,
        onStopRecording: () -> Unit,
        isRecordingState: AtomicBoolean
    ) {
        // State for current decibel value
        var decibelValue by remember { mutableStateOf(0.0) }
        var statusMessage by remember { mutableStateOf("Ready") }
        var statusColor by remember { mutableStateOf(Color.Black) }

        // Track whether we're actively recording
        var isRecordingActive by remember { mutableStateOf(isRecordingState.get()) }

        // Permission state using Accompanist Permissions library
        val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
        val permissionGranted = audioPermissionState.status.isGranted

        // Effect to update decibel values while recording
        // This runs in a coroutine to avoid blocking the UI thread
        LaunchedEffect(isRecordingActive) {
            if (isRecordingActive) {
                launch(Dispatchers.IO) {
                    val buffer = ShortArray(bufferSize / 2)

                    while (isActive && isRecordingState.get()) {
                        // Read audio data from microphone
                        val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                        if (readResult > 0) {
                            // Calculate amplitude from audio samples
                            val amplitude = calculateAmplitude(buffer, readResult)

                            // Convert amplitude to decibels
                            val db = calculateDecibels(amplitude)

                            // Update UI state with non-negative dB value
                            decibelValue = if (db < 0) 0.0 else db

                            // Update status message based on noise level
                            if (decibelValue >= THRESHOLD_DB) {
                                statusMessage = "Warning: High noise level!"
                                statusColor = Red
                            } else {
                                statusMessage = "Normal noise level"
                                statusColor = Green
                            }
                        }

                        // Limit update rate to reduce CPU usage
                        delay(100) // Update every 100ms
                    }
                }
            }
        }

        // Observe recording state changes
        LaunchedEffect(isRecordingState.get()) {
            isRecordingActive = isRecordingState.get()
        }

        // Main UI Surface
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "Sound Meter",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 32.dp)
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Current sound level label
                Text(
                    text = "Current Sound Level:",
                    fontSize = 16.sp
                )

                // Decibel value display - large and prominent
                Text(
                    text = "${String.format("%.1f", decibelValue)} dB",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Status message with color coding
                Text(
                    text = statusMessage,
                    fontSize = 16.sp,
                    color = statusColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Progress bar visualizing sound level
                // The color changes based on intensity
                val progressColor = when {
                    decibelValue >= THRESHOLD_DB -> Red        // Dangerous level
                    decibelValue >= THRESHOLD_DB - 10 -> Yellow // Warning level
                    else -> Green                               // Safe level
                }

                LinearProgressIndicator(
                    progress = (decibelValue / 100.0).toFloat().coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(horizontal = 16.dp),
                    color = progressColor
                )

                // Level indicators below progress bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Low")
                    Text(text = "Medium")
                    Text(text = "High")
                }

                Spacer(modifier = Modifier.weight(1f))

                // Conditional UI based on permission status
                if (!permissionGranted) {
                    // Show permission request UI if microphone access not granted
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 64.dp)
                    ) {
                        Text(
                            text = "Microphone permission is required",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Button(onClick = { audioPermissionState.launchPermissionRequest() }) {
                            Text("Request Permission")
                        }
                    }
                } else {
                    // Recording control buttons
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(bottom = 64.dp)
                    ) {
                        if (isRecordingActive) {
                            Button(
                                onClick = onStopRecording,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Red
                                )
                            ) {
                                Text("Stop Measuring")
                            }
                        } else {
                            Button(onClick = onStartRecording) {
                                Text("Start Measuring")
                            }
                        }
                    }
                }
            }
        }
    }
    private fun startRecording() {
        // Check for microphone permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request permission if not granted
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                0
            )
            return
        }

        // Initialize AudioRecord if not already created
        if (audioRecord == null) {
            try {
                // Create AudioRecord instance
                // Reference: Approach based on https://github.com/albertopasqualetto/SoundMeterESP
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,  // Use microphone as audio source
                    SAMPLE_RATE,                   // 44.1kHz sampling rate
                    CHANNEL_CONFIG,                // Mono channel
                    AUDIO_FORMAT,                  // 16-bit PCM
                    bufferSize                     // Buffer size calculated earlier
                )

                // Check if initialization was successful
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    return
                }
            } catch (e: Exception) {
                return
            }
        }

        try {
            // Start the actual recording process
            audioRecord?.startRecording()
            isRecording.set(true)
        } catch (e: Exception) {
            stopRecording()
        }
    }

    private fun stopRecording() {
        // Update recording state
        isRecording.set(false)

        try {
            // Stop AudioRecord
            audioRecord?.stop()
        } catch (e: Exception) {
            // Already stopped or never started
        }
    }

    private fun calculateAmplitude(buffer: ShortArray, readSize: Int): Double {
        var sum = 0.0

        // Sum the squares of all samples
        for (i in 0 until readSize) {
            sum += buffer[i] * buffer[i]
        }

        // Calculate Root Mean Square (RMS)
        // Reference: Method from https://github.com/albertopasqualetto/SoundMeterESP
        return sqrt(sum / readSize)
    }


    private fun calculateDecibels(amplitude: Double): Double {
        // Avoid log of zero or negative values
        if (amplitude <= 0) return 0.0

        val normalizedAmplitude = amplitude / 32767.0
        // Formula: dB = 20 * log10(amplitude / reference)
        // Reference: Implementation based on https://github.com/albertopasqualetto/SoundMeterESP
        return 20 * log10(normalizedAmplitude / REFERENCE_AMPLITUDE)
    }

    /**
     * Lifecycle method: called when activity is no longer visible
     */
    override fun onStop() {
        super.onStop()
        // Always stop recording when app goes to background
        stopRecording()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up resources to prevent memory leaks
        try {
            stopRecording()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            // Handle cleanup errors
        }
    }
}
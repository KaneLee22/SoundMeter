package com.example.soundmeter

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.soundmeter.databinding.ActivityMainBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.text.DecimalFormat
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_RECORD_AUDIO = 1001
        private const val DEFAULT_THRESHOLD = 85.0f
    }

    // View binding
    private lateinit var binding: ActivityMainBinding

    // Audio recorder instance
    private val audioRecorder = AudioRecorder()

    // Coroutine job for measurement
    private var measurementJob: Job? = null

    // Stats tracking
    private var minDb = Double.MAX_VALUE
    private var maxDb = 0.0
    private var totalDb = 0.0
    private var sampleCount = 0

    // Threshold value
    private var thresholdDb = DEFAULT_THRESHOLD

    // Decimal formatting for display
    private val decimalFormat = DecimalFormat("#0.0")

    // Alert sound player
    private var alertPlayer: MediaPlayer? = null
    private var isAlertPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize UI elements
        setupUI()

        // Request microphone permission if needed
        if (!hasMicrophonePermission()) {
            requestMicrophonePermission()
        }
    }

    private fun setupUI() {
        // Initialize sound meter view with threshold
        binding.soundMeterView.setThreshold(thresholdDb)

        // Set up threshold seek bar
        binding.thresholdSeekBar.progress = thresholdDb.roundToInt()
        binding.tvThresholdLabel.text = getString(R.string.threshold_label, thresholdDb)

        binding.thresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                thresholdDb = progress.toFloat()
                binding.tvThresholdLabel.text = getString(R.string.threshold_label, thresholdDb)
                binding.soundMeterView.setThreshold(thresholdDb)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Set up toggle button
        binding.btnToggle.setOnClickListener {
            toggleMeasurement()
        }

        // Initialize stats display
        updateStatsDisplay()
    }

    private fun toggleMeasurement() {
        if (audioRecorder.isRecording()) {
            stopMeasurement()
        } else {
            if (hasMicrophonePermission()) {
                startMeasurement()
            } else {
                requestMicrophonePermission()
            }
        }
    }

    private fun startMeasurement() {
        // Reset stats
        resetStats()

        // Start audio recording
        if (!audioRecorder.start()) {
            showErrorDialog("Could not start audio recording")
            return
        }

        // Update UI to "recording" state
        binding.btnToggle.text = getString(R.string.stop_measuring)

        // Start continuous measurement
        measurementJob = audioRecorder.startDecibelMeter()
            .onEach { decibelValue ->
                updateUI(decibelValue)
            }
            .catch { e ->
                e.printStackTrace()
                stopMeasurement()
                showErrorDialog("Error during measurement: ${e.message}")
            }
            .launchIn(lifecycleScope)
    }

    private fun stopMeasurement() {
        // Cancel measurement coroutine
        measurementJob?.cancel()
        measurementJob = null

        // Stop audio recording
        audioRecorder.stop()

        // Stop alert if playing
        stopAlertSound()

        // Update UI
        binding.btnToggle.text = getString(R.string.start_measuring)
        binding.tvWarning.visibility = View.INVISIBLE
    }

    private fun updateUI(decibelValue: Double) {
        // Update the current dB display
        binding.tvDecibelValue.text = getString(
            R.string.decibel_value,
            decimalFormat.format(decibelValue)
        )

        // Update sound meter view
        binding.soundMeterView.setDecibelLevel(decibelValue.toFloat())

        // Update stats
        updateStats(decibelValue)

        // Check threshold for alert
        if (decibelValue >= thresholdDb) {
            showAlert()
        } else {
            hideAlert()
        }
    }

    private fun updateStats(decibelValue: Double) {
        // Skip very low values that might be noise floor
        if (decibelValue < 10) return

        // Update min/max/avg
        if (decibelValue < minDb) minDb = decibelValue
        if (decibelValue > maxDb) maxDb = decibelValue

        totalDb += decibelValue
        sampleCount++

        // Update display
        updateStatsDisplay()
    }

    private fun updateStatsDisplay() {
        // Min display
        val minText = if (minDb == Double.MAX_VALUE) "0.0" else decimalFormat.format(minDb)
        binding.tvMinValue.text = getString(R.string.min_label, minText.toFloat())

        // Max display
        binding.tvMaxValue.text = getString(R.string.max_label, decimalFormat.format(maxDb).toFloat())

        // Average display
        val avgDb = if (sampleCount > 0) totalDb / sampleCount else 0.0
        binding.tvAvgValue.text = getString(R.string.avg_label, decimalFormat.format(avgDb).toFloat())
    }

    private fun resetStats() {
        minDb = Double.MAX_VALUE
        maxDb = 0.0
        totalDb = 0.0
        sampleCount = 0
        updateStatsDisplay()
    }

    private fun showAlert() {
        binding.tvWarning.visibility = View.VISIBLE

        // Play alert sound if not already playing
        if (!isAlertPlaying) {
            playAlertSound()
        }
    }

    private fun hideAlert() {
        binding.tvWarning.visibility = View.INVISIBLE
        stopAlertSound()
    }

    private fun playAlertSound() {
        // Simple beep sound using ToneGenerator would go here
        // For this example, we're just using a flag
        isAlertPlaying = true
    }

    private fun stopAlertSound() {
        isAlertPlaying = false
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMicrophonePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_RECORD_AUDIO
        )
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, can start recording
                if (binding.btnToggle.text == getString(R.string.stop_measuring)) {
                    startMeasurement()
                }
            } else {
                // Permission denied
                showErrorDialog("Microphone permission is required for this app to work")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMeasurement()
        alertPlayer?.release()
        alertPlayer = null
    }
}
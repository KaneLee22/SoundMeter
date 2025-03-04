package com.example.soundmeter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * Custom view that displays a sound level meter with color gradients based on decibel values.
 */
class SoundMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paint objects for drawing
    private val backgroundPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.meter_background)
        style = Paint.Style.FILL
    }

    private val levelPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val segmentPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.black)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 50
    }

    private val textPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 30f
        textAlign = Paint.Align.RIGHT
    }

    // Colors for different sound levels
    private val lowColor = ContextCompat.getColor(context, R.color.sound_low)
    private val mediumColor = ContextCompat.getColor(context, R.color.sound_medium)
    private val highColor = ContextCompat.getColor(context, R.color.sound_high)

    // Drawing areas
    private val meterRect = RectF()
    private val levelRect = RectF()

    // Sound level values
    private var currentDb = 0f
    private var thresholdDb = 85f

    // Decibel range to display
    private val minDb = 0f
    private val maxDb = 120f

    // Number of segments to draw in the meter
    private val segmentCount = 30

    /**
     * Set the current decibel value to display
     */
    fun setDecibelLevel(db: Float) {
        currentDb = db.coerceIn(minDb, maxDb)
        invalidate()
    }

    /**
     * Set the threshold at which the color changes to warning level
     */
    fun setThreshold(threshold: Float) {
        thresholdDb = threshold
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Set the area for the meter
        val padding = 40f
        meterRect.set(padding, padding, width - padding, height - padding)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background
        canvas.drawRoundRect(meterRect, 10f, 10f, backgroundPaint)

        // Calculate level width based on current dB value
        val levelWidth = calculateLevelWidth()
        levelRect.set(meterRect.left, meterRect.top, levelWidth, meterRect.bottom)

        // Set level color based on value
        levelPaint.color = when {
            currentDb < thresholdDb * 0.7 -> lowColor
            currentDb < thresholdDb -> mediumColor
            else -> highColor
        }

        // Draw the level
        canvas.drawRoundRect(levelRect, 10f, 10f, levelPaint)

        // Draw segments
        drawSegments(canvas)

        // Draw dB markings
        drawDbMarkings(canvas)
    }

    private fun calculateLevelWidth(): Float {
        val percentage = (currentDb - minDb) / (maxDb - minDb)
        return meterRect.left + (meterRect.width() * percentage)
    }

    private fun drawSegments(canvas: Canvas) {
        val segmentWidth = meterRect.width() / segmentCount

        for (i in 1 until segmentCount) {
            val x = meterRect.left + i * segmentWidth
            canvas.drawLine(x, meterRect.top, x, meterRect.bottom, segmentPaint)
        }
    }

    private fun drawDbMarkings(canvas: Canvas) {
        // Draw dB markings at 30dB intervals
        val markingTops = arrayOf(30f, 60f, 90f, 120f)

        for (db in markingTops) {
            val percentage = (db - minDb) / (maxDb - minDb)
            val x = meterRect.left + (meterRect.width() * percentage)

            // Draw db value text
            canvas.drawText(
                "${db.toInt()}",
                x,
                meterRect.bottom + 35f,
                textPaint
            )
        }
    }
}
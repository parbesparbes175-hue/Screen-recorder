package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.storage.MediaStoreHelper

class FloatingControlManager(
    private val context: Context,
    private val onPauseResumeClicked: () -> Unit,
    private val onStopClicked: () -> Unit
) {
    companion object {
        private const val TAG = "FloatingControlManager"
    }

    private var windowManager: WindowManager? = null
    private var floatingView: LinearLayout? = null
    private var timerTextView: TextView? = null
    private var pauseResumeIcon: ImageView? = null
    private var isPaused = false

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (floatingView != null) return

        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted; floating control skipped.")
            return
        }

        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dpToPx(16)
                y = dpToPx(120) // Default positioned away from top game bars
            }

            // Create root container
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))

                // High-performance dark pill background with subtle glow
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#E612161A")) // 90% opacity dark carbon
                    cornerRadius = dpToPx(24).toFloat()
                    setStroke(dpToPx(1), Color.parseColor("#3300E676")) // subtle emerald border
                }
            }

            // Red recording indicator dot
            val dot = View(context).apply {
                val size = dpToPx(8)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dpToPx(6)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#FF1744"))
                }
            }
            root.addView(dot)

            // Timer text
            val timerText = TextView(context).apply {
                text = "00:00"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(0, 0, dpToPx(10), 0)
            }
            timerTextView = timerText
            root.addView(timerText)

            // Pause / Resume button
            val pauseButton = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                    marginEnd = dpToPx(6)
                }
                setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                setImageResource(android.R.drawable.ic_media_pause)
                setColorFilter(Color.parseColor("#00E676"))
                setOnClickListener {
                    onPauseResumeClicked()
                }
            }
            pauseResumeIcon = pauseButton
            root.addView(pauseButton)

            // Stop button
            val stopButton = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28))
                setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                setImageResource(android.R.drawable.checkbox_off_background) // Square stop symbol
                setColorFilter(Color.parseColor("#FF1744"))
                setOnClickListener {
                    onStopClicked()
                }
            }
            root.addView(stopButton)

            // Drag touch listener
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            root.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(root, params)
                        true
                    }
                    else -> false
                }
            }

            windowManager?.addView(root, params)
            floatingView = root
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create floating overlay: ${e.message}", e)
        }
    }

    fun updateTimer(durationMs: Long) {
        val formatted = MediaStoreHelper.formatDuration(durationMs)
        floatingView?.post {
            timerTextView?.text = formatted
        }
    }

    fun setPaused(paused: Boolean) {
        isPaused = paused
        floatingView?.post {
            if (paused) {
                pauseResumeIcon?.setImageResource(android.R.drawable.ic_media_play)
                pauseResumeIcon?.setColorFilter(Color.parseColor("#FFC107")) // Amber when paused
            } else {
                pauseResumeIcon?.setImageResource(android.R.drawable.ic_media_pause)
                pauseResumeIcon?.setColorFilter(Color.parseColor("#00E676")) // Emerald when active
            }
        }
    }

    fun hide() {
        try {
            if (floatingView != null && windowManager != null) {
                windowManager?.removeView(floatingView)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing floating view: ${e.message}")
        } finally {
            floatingView = null
            timerTextView = null
            pauseResumeIcon = null
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}

package com.ctrl_tab.android_desktop_touchpad

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class CursorService : AccessibilityService() {

    companion object {
        var instance: CursorService? = null
        private const val TAG = "CursorService"
        private const val FLAG_PRESENTATION = 1 shl 6
    }

    private lateinit var displayManager: DisplayManager
    private var windowManager: WindowManager? = null
    private var cursorView: CursorView? = null
    private lateinit var prefs: SharedPreferences
    private var targetDisplayId = Display.DEFAULT_DISPLAY
    private var cursorX = 500f
    private var cursorY = 500f
    private val mainHandler = Handler(Looper.getMainLooper())

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            updateDisplayAndCursor()
            mainHandler.postDelayed(this, 1000)
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "cursor_size" || key == "cursor_color") updateCursorAppearance()
    }

    inner class CursorView(context: Context) : View(context) {
        var color: Int = Color.RED
        var cursorSize: Int = 40
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = 2f
        }
        override fun onDraw(canvas: Canvas) {
            val s = cursorSize.toFloat()
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(0f, s * 0.85f)
                lineTo(s * 0.25f, s * 0.6f)
                lineTo(s * 0.45f, s)
                lineTo(s * 0.57f, s * 0.95f)
                lineTo(s * 0.37f, s * 0.55f)
                lineTo(s * 0.7f, s * 0.55f)
                close()
            }
            fillPaint.color = color
            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, strokePaint)
        }
    }

    override fun onServiceConnected() {
        instance = this
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        prefs = getSharedPreferences("CursorSettings", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        displayManager.registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) {
                mainHandler.postDelayed({ updateDisplayAndCursor() }, 500)
            }
            override fun onDisplayRemoved(id: Int) {
                mainHandler.post { updateDisplayAndCursor() }
            }
            override fun onDisplayChanged(id: Int) {
                mainHandler.post { updateDisplayAndCursor() }
            }
        }, mainHandler)

        updateDisplayAndCursor()
        mainHandler.post(heartbeatRunnable)
    }

    fun updateDisplayAndCursor() {
        val displays = displayManager.displays
        val physicalSecondary = displays.firstOrNull { d ->
            d.displayId != Display.DEFAULT_DISPLAY &&
                    (d.flags and FLAG_PRESENTATION) == 0
        }
        val target = physicalSecondary
            ?: displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        val newId = target?.displayId ?: Display.DEFAULT_DISPLAY

        if (newId == targetDisplayId && windowManager != null && cursorView != null) {
            bringCursorToFront()
            return
        }

        cursorView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) { Log.e(TAG, "removeView: $e") }
        }
        cursorView = null
        windowManager = null
        targetDisplayId = newId

        if (targetDisplayId != Display.DEFAULT_DISPLAY) {
            val targetDisplay = displayManager.getDisplay(targetDisplayId)
            if (targetDisplay != null) {
                val displayContext = createDisplayContext(targetDisplay)
                windowManager = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                createVisualCursor()
            }
        }
    }

    private fun bringCursorToFront() {
        val wm = windowManager ?: return
        cursorView?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        cursorView = null
        createVisualCursor()
    }

    private fun createVisualCursor() {
        val size = prefs.getInt("cursor_size", 40)
        val color = prefs.getInt("cursor_color", Color.RED)
        cursorView = CursorView(this).apply {
            this.color = color
            this.cursorSize = size
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = cursorX.toInt()
            y = cursorY.toInt()
        }
        try {
            windowManager?.addView(cursorView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add cursor: $e")
        }
    }

    fun toggleCursor(hide: Boolean) {
        cursorView?.post { cursorView?.visibility = if (hide) View.GONE else View.VISIBLE }
    }

    fun updateCursorAppearance() {
        val size = prefs.getInt("cursor_size", 40)
        val color = prefs.getInt("cursor_color", Color.RED)
        cursorView?.let { view ->
            view.post {
                (view as? CursorView)?.let { it.color = color; it.cursorSize = size; it.invalidate() }
                val p = view.layoutParams as? WindowManager.LayoutParams ?: return@post
                p.width = size; p.height = size
                try { windowManager?.updateViewLayout(view, p) } catch (e: Exception) {}
            }
        }
    }

    fun moveCursor(dx: Float, dy: Float) {
        val wm = windowManager ?: return
        val metrics = DisplayMetrics()
        displayManager.getDisplay(targetDisplayId)?.getRealMetrics(metrics) ?: return
        cursorX = (cursorX + dx).coerceIn(0f, metrics.widthPixels.toFloat() - 5)
        cursorY = (cursorY + dy).coerceIn(0f, metrics.heightPixels.toFloat() - 5)
        cursorView?.let {
            val p = it.layoutParams as? WindowManager.LayoutParams ?: return
            p.x = cursorX.toInt(); p.y = cursorY.toInt()
            try { wm.updateViewLayout(it, p) } catch (e: Exception) { Log.e(TAG, "moveCursor: $e") }
        }
    }

    fun performClick(isRight: Boolean) {
        val path = Path().apply { moveTo(cursorX, cursorY) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, if (isRight) 600L else 50L))
            .setDisplayId(targetDisplayId).build(), null, null)
    }

    fun scroll(dy: Float) {
        val path = Path().apply { moveTo(cursorX, cursorY); lineTo(cursorX, cursorY + (dy * 15)) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .setDisplayId(targetDisplayId).build(), null, null)
    }

    fun zoom(zoomIn: Boolean) {
        val d = if (zoomIn) 150f else 20f; val ed = if (zoomIn) 20f else 150f
        val p1 = Path().apply { moveTo(cursorX - d, cursorY); lineTo(cursorX - ed, cursorY) }
        val p2 = Path().apply { moveTo(cursorX + d, cursorY); lineTo(cursorX + ed, cursorY) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p1, 0, 200))
            .addStroke(GestureDescription.StrokeDescription(p2, 0, 200))
            .setDisplayId(targetDisplayId).build(), null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    override fun onInterrupt() {
        mainHandler.removeCallbacks(heartbeatRunnable)
        instance = null
    }
}
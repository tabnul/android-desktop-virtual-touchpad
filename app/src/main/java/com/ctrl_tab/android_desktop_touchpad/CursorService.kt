package com.ctrl_tab.android_desktop_touchpad

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView

class CursorService : AccessibilityService() {

    companion object {
        var instance: CursorService? = null
    }

    private lateinit var displayManager: DisplayManager
    private var windowManager: WindowManager? = null
    private var cursorView: ImageView? = null
    private lateinit var prefs: SharedPreferences

    private var targetDisplayId = Display.DEFAULT_DISPLAY
    private var cursorX = 500f
    private var cursorY = 500f

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "cursor_size" || key == "cursor_color") updateCursorAppearance()
    }

    // EXACT DEZELFDE LISTENER DIE GISTEREN WERKTE
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) { updateDisplayAndCursor() }
        override fun onDisplayRemoved(displayId: Int) { updateDisplayAndCursor() }
        override fun onDisplayChanged(displayId: Int) {}
    }

    override fun onServiceConnected() {
        instance = this
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)

        prefs = getSharedPreferences("CursorSettings", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        updateDisplayAndCursor()
    }

    private fun updateDisplayAndCursor() {
        val displays = displayManager.displays
        val newDisplayId = if (displays.size > 1) displays[displays.size - 1].displayId else Display.DEFAULT_DISPLAY

        if (newDisplayId != targetDisplayId || cursorView == null) {
            cursorView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
            cursorView = null
            targetDisplayId = newDisplayId

            if (targetDisplayId != Display.DEFAULT_DISPLAY) {
                val targetDisplay = displayManager.getDisplay(targetDisplayId)
                val displayContext = createDisplayContext(targetDisplay)
                windowManager = displayContext.getSystemService(WINDOW_SERVICE) as WindowManager
                createVisualCursor()
            } else {
                windowManager = null
            }
        }
    }

    private fun createVisualCursor() {
        cursorView = ImageView(this).apply {
            setImageResource(android.R.drawable.presence_online)
            setColorFilter(prefs.getInt("cursor_color", Color.RED))
        }
        updateCursorAppearance()
        val params = WindowManager.LayoutParams(
            prefs.getInt("cursor_size", 40), prefs.getInt("cursor_size", 40),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = cursorX.toInt()
            y = cursorY.toInt()
        }
        try { windowManager?.addView(cursorView, params) } catch (e: Exception) {}
    }

    private fun updateCursorAppearance() {
        val size = prefs.getInt("cursor_size", 30)
        val color = prefs.getInt("cursor_color", Color.RED)
        cursorView?.let { view ->
            view.setImageResource(android.R.drawable.presence_online)
            view.setColorFilter(color)
            val params = view.layoutParams as? WindowManager.LayoutParams
            if (params != null) {
                params.width = size
                params.height = size
                windowManager?.updateViewLayout(view, params)
            }
        }
    }

    fun moveCursor(dx: Float, dy: Float) {
        val wm = windowManager ?: return
        val display = displayManager.getDisplay(targetDisplayId) ?: return
        val metrics = android.util.DisplayMetrics()
        display.getRealMetrics(metrics)

        cursorX = (cursorX + dx).coerceIn(0f, metrics.widthPixels.toFloat() - 5)
        cursorY = (cursorY + dy).coerceIn(0f, metrics.heightPixels.toFloat() - 5)

        cursorView?.let {
            val p = it.layoutParams as WindowManager.LayoutParams
            p.x = cursorX.toInt(); p.y = cursorY.toInt()
            wm.updateViewLayout(it, p)
        }
    }

    fun performClick(isRight: Boolean) {
        val path = Path().apply { moveTo(cursorX, cursorY) }
        val duration = if (isRight) 600L else 50L
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .setDisplayId(targetDisplayId).build()
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() { instance = null }
}
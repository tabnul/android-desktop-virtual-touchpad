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

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) { updateDisplayAndCursor() }
        override fun onDisplayRemoved(displayId: Int) { updateDisplayAndCursor() }
        override fun onDisplayChanged(displayId: Int) { updateDisplayAndCursor() }
    }

    override fun onServiceConnected() {
        // De volgorde is hier cruciaal om crashes te voorkomen
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)

        prefs = getSharedPreferences("CursorSettings", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        instance = this // Pas als alles geladen is, zetten we de instance
        updateDisplayAndCursor()
    }

    fun updateDisplayAndCursor() {
        val displays = displayManager.displays
        val newDisplayId = if (displays.size > 1) displays[displays.size - 1].displayId else Display.DEFAULT_DISPLAY

        if (newDisplayId != targetDisplayId || cursorView == null) {
            cursorView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
            cursorView = null
            targetDisplayId = newDisplayId

            if (targetDisplayId != Display.DEFAULT_DISPLAY) {
                try {
                    val targetDisplay = displayManager.getDisplay(targetDisplayId)
                    val displayContext = createDisplayContext(targetDisplay)
                    windowManager = displayContext.getSystemService(WINDOW_SERVICE) as WindowManager
                    createVisualCursor()
                } catch (e: Exception) {
                    android.util.Log.e("CursorService", "Failed to setup display context: ${e.message}")
                }
            }
        }
    }

    private fun createVisualCursor() {
        cursorView = ImageView(this).apply {
            setImageResource(android.R.drawable.presence_online)
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

    fun updateCursorAppearance() {
        val size = prefs.getInt("cursor_size", 40)
        val color = prefs.getInt("cursor_color", Color.RED)
        cursorView?.let { view ->
            view.setColorFilter(color)
            val params = view.layoutParams as? WindowManager.LayoutParams
            if (params != null) {
                params.width = size; params.height = size
                try { windowManager?.updateViewLayout(view, params) } catch(e: Exception) {}
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
            try { wm.updateViewLayout(it, p) } catch(e: Exception) {}
        }
    }

    fun performClick(isRight: Boolean) {
        val path = Path().apply { moveTo(cursorX, cursorY) }
        val duration = if (isRight) 600L else 50L
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .setDisplayId(targetDisplayId).build(), null, null)
    }

    fun scroll(dy: Float) {
        val path = Path().apply { moveTo(cursorX, cursorY); lineTo(cursorX, cursorY + (dy * 15)) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 100)).setDisplayId(targetDisplayId).build(), null, null)
    }

    fun zoom(zoomIn: Boolean) {
        val d = if(zoomIn) 150f else 20f; val ed = if(zoomIn) 20f else 150f
        val p1 = Path().apply { moveTo(cursorX - d, cursorY); lineTo(cursorX - ed, cursorY) }
        val p2 = Path().apply { moveTo(cursorX + d, cursorY); lineTo(cursorX + ed, cursorY) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p1, 0, 200)).addStroke(GestureDescription.StrokeDescription(p2, 0, 200)).setDisplayId(targetDisplayId).build(), null, null)
    }

    fun swipeNav(forward: Boolean) {
        val startX = if (forward) cursorX - 300 else cursorX + 300
        val path = Path().apply { moveTo(startX, cursorY); lineTo(cursorX, cursorY) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 150)).setDisplayId(targetDisplayId).build(), null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() { instance = null }
}
package com.ctrl_tab.android_desktop_touchpad

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView

class CursorService : AccessibilityService() {

    companion object { var instance: CursorService? = null }

    private lateinit var displayManager: DisplayManager
    private var windowManager: WindowManager? = null
    private var cursorView: ImageView? = null
    private lateinit var prefs: SharedPreferences
    private var targetDisplayId = Display.DEFAULT_DISPLAY
    private var cursorX = 500f
    private var cursorY = 500f

    // ✅ LIVE UPDATE: Luister naar wijzigingen in kleur of grootte
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "cursor_size" || key == "cursor_color") {
            updateCursorAppearance()
        }
    }

    override fun onServiceConnected() {
        instance = this
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        prefs = getSharedPreferences("CursorSettings", Context.MODE_PRIVATE)

        // Registreer de listener
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        displayManager.registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) { Handler(Looper.getMainLooper()).postDelayed({ updateDisplayAndCursor() }, 500) }
            override fun onDisplayRemoved(id: Int) { updateDisplayAndCursor() }
            override fun onDisplayChanged(id: Int) { updateDisplayAndCursor() }
        }, null)

        updateDisplayAndCursor()
    }

    fun toggleCursor(hide: Boolean) {
        cursorView?.post { cursorView?.visibility = if (hide) View.GONE else View.VISIBLE }
    }

    fun updateDisplayAndCursor() {
        val displays = displayManager.displays
        val newId = if (displays.size > 1) displays[displays.size - 1].displayId else Display.DEFAULT_DISPLAY
        cursorView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
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

    private fun createVisualCursor() {
        cursorView = ImageView(this).apply { setImageResource(android.R.drawable.presence_online) }
        updateCursorAppearance()

        val size = prefs.getInt("cursor_size", 40)
        val params = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT; x = cursorX.toInt(); y = cursorY.toInt() }
        try { windowManager?.addView(cursorView, params) } catch (e: Exception) {}
    }

    // ✅ HIER GEBEURT DE MAGIE: Update kleur en grootte van de bestaande View
    fun updateCursorAppearance() {
        val size = prefs.getInt("cursor_size", 40)
        val color = prefs.getInt("cursor_color", Color.RED)
        cursorView?.let { view ->
            view.post {
                view.setColorFilter(color)
                val p = view.layoutParams as? WindowManager.LayoutParams
                if (p != null) {
                    p.width = size
                    p.height = size
                    try { windowManager?.updateViewLayout(view, p) } catch (e: Exception) {}
                }
            }
        }
    }

    fun moveCursor(dx: Float, dy: Float) {
        val wm = windowManager ?: return
        val metrics = DisplayMetrics()
        displayManager.getDisplay(targetDisplayId).getRealMetrics(metrics)
        cursorX = (cursorX + dx).coerceIn(0f, metrics.widthPixels.toFloat() - 5)
        cursorY = (cursorY + dy).coerceIn(0f, metrics.heightPixels.toFloat() - 5)
        cursorView?.let {
            val p = it.layoutParams as WindowManager.LayoutParams
            p.x = cursorX.toInt(); p.y = cursorY.toInt()
            try { wm.updateViewLayout(it, p) } catch (e: Exception) {}
        }
    }

    fun performClick(isRight: Boolean) {
        val path = Path().apply { moveTo(cursorX, cursorY) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, if (isRight) 600L else 50L)).setDisplayId(targetDisplayId).build(), null, null)
    }

    fun scroll(dy: Float) {
        val path = Path().apply { moveTo(cursorX, cursorY); lineTo(cursorX, cursorY + (dy * 15)) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 100)).setDisplayId(targetDisplayId).build(), null, null)
    }

    fun zoom(zoomIn: Boolean) {
        val d = if (zoomIn) 150f else 20f; val ed = if (zoomIn) 20f else 150f
        val p1 = Path().apply { moveTo(cursorX - d, cursorY); lineTo(cursorX - ed, cursorY) }
        val p2 = Path().apply { moveTo(cursorX + d, cursorY); lineTo(cursorX + ed, cursorY) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p1, 0, 200)).addStroke(GestureDescription.StrokeDescription(p2, 0, 200)).setDisplayId(targetDisplayId).build(), null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() { instance = null }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }
}
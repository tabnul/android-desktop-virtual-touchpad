package com.ctrl_tab.android_desktop_touchpad

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.hypot

class CursorService : AccessibilityService() {

    private lateinit var displayManager: DisplayManager
    private var windowManager: WindowManager? = null
    private lateinit var touchpadView: FrameLayout
    private var cursorView: ImageView? = null
    private lateinit var prefs: SharedPreferences

    private var targetDisplayId = Display.DEFAULT_DISPLAY
    private var cursorX = 500f
    private var cursorY = 500f
    private var lastX = 0f
    private var lastY = 0f
    private val moveThreshold = 5
    private var isMoving = false
    private var maxPointersInGesture = 0
    private var isMinimized = false

    private var initialTouchpadX = 0
    private var initialTouchpadY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialPinchDistance = 0f

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "cursor_size" || key == "cursor_color") updateCursorAppearance()
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) { updateDisplayAndCursor() }
        override fun onDisplayRemoved(displayId: Int) { updateDisplayAndCursor() }
        override fun onDisplayChanged(displayId: Int) {}
    }

    override fun onServiceConnected() {
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)
        prefs = getSharedPreferences("CursorSettings", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        createTouchpadOnPhone()
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
        cursorView = ImageView(this)
        updateCursorAppearance()
        val size = prefs.getInt("cursor_size", 30)
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.LEFT
        params.x = cursorX.toInt()
        params.y = cursorY.toInt()
        windowManager?.addView(cursorView, params)
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

    @SuppressLint("ClickableViewAccessibility")
    private fun createTouchpadOnPhone() {
        val phoneWM = getSystemService(WINDOW_SERVICE) as WindowManager

        // Touchpad styling met BORDER
        val border = GradientDrawable().apply {
            setColor(0x66000000) // Achtergrondkleur (transparant zwart)
            setStroke(4, Color.WHITE) // De omkadering: 4px dik, wit
            cornerRadius = 10f
        }

        touchpadView = FrameLayout(this).apply {
            background = border
        }

        // 1. Drag Handle (Links)
        val dragHandle = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_sort_by_size)
            setColorFilter(Color.WHITE)
            setBackgroundColor(0x22FFFFFF)
            setPadding(15, 0, 15, 0)
        }
        val handleParams = FrameLayout.LayoutParams(100, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START)
        touchpadView.addView(dragHandle, handleParams)

        // 2. Minimaliseer/Maximaliseer knop (Rechts)
        val toggleBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel) // Kruisje om te minimaliseren
            setColorFilter(Color.LTGRAY)
            setBackgroundColor(0x22FFFFFF)
            setPadding(20, 20, 20, 20)
        }
        val toggleParams = FrameLayout.LayoutParams(100, 100, Gravity.TOP or Gravity.END)
        touchpadView.addView(toggleBtn, toggleParams)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, 500,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; y = 1000 }

        // Minimaliseer logica
        toggleBtn.setOnClickListener {
            isMinimized = !isMinimized
            if (isMinimized) {
                params.width = 150 // Wordt een klein blokje
                params.height = 150
                toggleBtn.setImageResource(android.R.drawable.ic_menu_add) // Plusje om terug te keren
                dragHandle.visibility = View.GONE
            } else {
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.height = 500
                toggleBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                dragHandle.visibility = View.VISIBLE
            }
            phoneWM.updateViewLayout(touchpadView, params)
        }

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    initialTouchpadX = params.x; initialTouchpadY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialTouchpadX + (event.rawX - initialTouchX).toInt()
                    params.y = initialTouchpadY + (event.rawY - initialTouchY).toInt()
                    phoneWM.updateViewLayout(touchpadView, params)
                    true
                }
                else -> false
            }
        }

        touchpadView.setOnTouchListener { _, event ->
            if (isMinimized) return@setOnTouchListener false // Geen cursor input als geminimaliseerd

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x; lastY = event.y
                    isMoving = false; maxPointersInGesture = 1
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    maxPointersInGesture = event.pointerCount
                    if (event.pointerCount == 2) initialPinchDistance = getDistance(event)
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - lastX
                    val deltaY = event.y - lastY

                    if (event.pointerCount == 1 && maxPointersInGesture == 1) {
                        if (Math.abs(deltaX) > moveThreshold || Math.abs(deltaY) > moveThreshold) isMoving = true
                        updateCursorPosition(deltaX * 2.5f, deltaY * 2.5f)
                    } else if (event.pointerCount == 2) {
                        val currentDistance = getDistance(event)
                        if (Math.abs(currentDistance - initialPinchDistance) > 50f) {
                            pinchZoom(currentDistance > initialPinchDistance)
                            initialPinchDistance = currentDistance
                        } else {
                            scrollVertical(deltaY)
                        }
                    }
                    lastX = event.x; lastY = event.y
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoving && maxPointersInGesture == 1) clickAt(cursorX, cursorY)
                    else if (!isMoving && maxPointersInGesture == 2) rightClickAt(cursorX, cursorY)
                    maxPointersInGesture = 0
                }
            }
            true
        }
        phoneWM.addView(touchpadView, params)
    }

    private fun getDistance(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return hypot(dx, dy)
    }

    private fun pinchZoom(zoomIn: Boolean) {
        val path1 = Path().apply { moveTo(cursorX - 100, cursorY); lineTo(if (zoomIn) cursorX - 10 else cursorX - 200, cursorY) }
        val path2 = Path().apply { moveTo(cursorX + 100, cursorY); lineTo(if (zoomIn) cursorX + 10 else cursorX + 200, cursorY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path1, 0, 200))
            .addStroke(GestureDescription.StrokeDescription(path2, 0, 200))
            .setDisplayId(targetDisplayId).build()
        dispatchGesture(gesture, null, null)
    }

    private fun updateCursorPosition(dx: Float, dy: Float) {
        val display = displayManager.getDisplay(targetDisplayId) ?: return
        val metrics = android.util.DisplayMetrics()
        display.getRealMetrics(metrics)
        cursorX = (cursorX + dx).coerceIn(0f, metrics.widthPixels.toFloat() - 5)
        cursorY = (cursorY + dy).coerceIn(0f, metrics.heightPixels.toFloat() - 5)
        cursorView?.let {
            val p = it.layoutParams as WindowManager.LayoutParams
            p.x = cursorX.toInt(); p.y = cursorY.toInt()
            windowManager?.updateViewLayout(it, p)
        }
    }

    private fun clickAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 50)).setDisplayId(targetDisplayId).build(), null, null)
    }

    private fun rightClickAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 600)).setDisplayId(targetDisplayId).build(), null, null)
    }

    private fun scrollVertical(distance: Float) {
        val path = Path().apply { moveTo(cursorX, cursorY); lineTo(cursorX, cursorY + (distance * 10)) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 100)).setDisplayId(targetDisplayId).build(), null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        displayManager.unregisterDisplayListener(displayListener)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        try {
            val phoneWM = getSystemService(WINDOW_SERVICE) as WindowManager
            phoneWM.removeView(touchpadView)
            cursorView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}
}
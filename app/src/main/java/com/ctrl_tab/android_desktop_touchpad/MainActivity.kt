package com.ctrl_tab.android_desktop_touchpad

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    private var lastX = 0f
    private var lastY = 0f
    private var initialPinchDist = 0f
    private var isMoving = false
    private var maxPointers = 0
    private var touchSlop = 0f

    override fun onResume() {
        super.onResume()
        CursorService.instance?.updateDisplayAndCursor()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        val sharedPref = getSharedPreferences("CursorSettings", Context.MODE_PRIVATE)

        val root = FrameLayout(this)

        // 1. Touchpad (Background)
        val touchpad = View(this).apply { setBackgroundColor(Color.parseColor("#D3D3D3")) }
        root.addView(touchpad)

        // 2. Control Overlay
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
            setBackgroundColor(Color.parseColor("#CCFFFFFF"))
        }

        // --- Top Bar with Icons ---
        val topBar = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        val btnHelp = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_help)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { showHelp() }
            id = View.generateViewId()
        }

        val btnSettings = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_preferences)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        val paramsHelp = RelativeLayout.LayoutParams(-2, -2).apply { addRule(RelativeLayout.ALIGN_PARENT_LEFT) }
        val paramsSettings = RelativeLayout.LayoutParams(-2, -2).apply { addRule(RelativeLayout.ALIGN_PARENT_RIGHT) }

        topBar.addView(btnHelp, paramsHelp)
        topBar.addView(btnSettings, paramsSettings)
        controls.addView(topBar)

        // --- Sliders & Colors ---
        controls.addView(TextView(this).apply { text = "Cursor Size"; setTextColor(Color.BLACK); setPadding(0, 20, 0, 0) })
        controls.addView(SeekBar(this).apply {
            max = 150
            progress = sharedPref.getInt("cursor_size", 40)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                    sharedPref.edit().putInt("cursor_size", p.coerceAtLeast(10)).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        controls.addView(TextView(this).apply { text = "Cursor Color"; setTextColor(Color.BLACK); setPadding(0, 20, 0, 0) })
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val colorOptions = mapOf("Red" to Color.RED, "Blue" to Color.BLUE, "Green" to Color.parseColor("#008000"))

        for ((name, col) in colorOptions) {
            colorRow.addView(Button(this).apply {
                text = name
                setTextColor(col)
                setBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(5, 5, 5, 5) }
                setOnClickListener { sharedPref.edit().putInt("cursor_color", col).apply() }
            })
        }
        controls.addView(colorRow)

        root.addView(controls, FrameLayout.LayoutParams(-1, -2))
        setContentView(root)

        // --- Touchpad Logic ---
        touchpad.setOnTouchListener { _, event ->
            val service = CursorService.instance ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x; lastY = event.y
                    isMoving = false; maxPointers = 1
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount > maxPointers) maxPointers = event.pointerCount
                    if (event.pointerCount == 2) initialPinchDist = calculateDist(event)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX; val dy = event.y - lastY
                    if (!isMoving && hypot(dx, dy) > touchSlop) isMoving = true
                    if (event.pointerCount == 1) service.moveCursor(dx * 2.5f, dy * 2.5f)
                    else if (event.pointerCount == 2) handleGestures(event, dx, dy, service)
                    lastX = event.x; lastY = event.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isMoving) {
                        if (maxPointers == 1) service.performClick(false)
                        else if (maxPointers == 2) service.performClick(true)
                    }
                }
            }
            true
        }
    }

    private fun handleGestures(e: MotionEvent, dx: Float, dy: Float, s: CursorService) {
        try {
            val dist = calculateDist(e)
            if (initialPinchDist > 0 && Math.abs(dist - initialPinchDist) > 80) {
                s.zoom(dist > initialPinchDist); initialPinchDist = dist
            } else if (Math.abs(dx) > 60 && Math.abs(dy) < 30) {
                s.swipeNav(dx > 0); lastX = e.x
            } else if (Math.abs(dy) > 5) { s.scroll(dy) }
        } catch (ex: Exception) {}
    }

    private fun calculateDist(e: MotionEvent): Float {
        return try { hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)) } catch (ex: Exception) { 0f }
    }

    private fun showHelp() {
        AlertDialog.Builder(this).setTitle("Gestures")
            .setMessage("1 Finger: Move\n1 Tap: Left Click\n2 Finger Tap: Right Click\n2 Finger Slide: Scroll\nPinch: Zoom\n2 Finger Swipe: Back/Forward")
            .setPositiveButton("OK", null).show()
    }
}
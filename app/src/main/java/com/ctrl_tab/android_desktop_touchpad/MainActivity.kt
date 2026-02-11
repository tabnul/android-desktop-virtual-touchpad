package com.ctrl_tab.android_desktop_touchpad

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    private var lastX = 0f; private var lastY = 0f
    private var initialPinchDist = 0f
    private var isMoving = false; private var maxPointers = 0

    override fun onResume() {
        super.onResume()
        // Dwing de service om de monitor te zoeken zodra de app weer zichtbaar wordt
        CursorService.instance?.updateDisplayAndCursor()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPref = getSharedPreferences("CursorSettings", Context.MODE_PRIVATE)

        val root = FrameLayout(this)
        val touchpad = View(this).apply { setBackgroundColor(Color.parseColor("#D3D3D3")) }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.parseColor("#CCFFFFFF"))
        }

        // Help & Settings Row
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(Button(this).apply {
            text = "Settings"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        row1.addView(Button(this).apply {
            text = "Help"; setOnClickListener { showHelp() }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        controls.addView(row1)

        // Size Slider
        controls.addView(TextView(this).apply { text = "Cursor Size"; setTextColor(Color.BLACK); setPadding(0,20,0,0) })
        controls.addView(SeekBar(this).apply {
            max = 150; progress = sharedPref.getInt("cursor_size", 40)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) { sharedPref.edit().putInt("cursor_size", p.coerceAtLeast(10)).apply() }
                override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        // Color Buttons
        controls.addView(TextView(this).apply { text = "Cursor Color"; setTextColor(Color.BLACK); setPadding(0,20,0,0) })
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val colors = mapOf("Red" to Color.RED, "Blue" to Color.BLUE, "Green" to Color.parseColor("#008000"))
        for ((name, col) in colors) {
            colorRow.addView(Button(this).apply {
                text = name; setTextColor(col); setBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(5,5,5,5) }
                setOnClickListener { sharedPref.edit().putInt("cursor_color", col).apply() }
            })
        }
        controls.addView(colorRow)

        root.addView(touchpad); root.addView(controls, FrameLayout.LayoutParams(-1, -2))
        setContentView(root)

        touchpad.setOnTouchListener { _, event ->
            // VEILIGHEIDSCHECK: Als de service nog niet aan staat, toon een melding ipv te crashen
            val service = CursorService.instance
            if (service == null) {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    Toast.makeText(this, "Please enable the Accessibility Service first!", Toast.LENGTH_SHORT).show()
                }
                return@setOnTouchListener false
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; isMoving = false; maxPointers = 1 }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    maxPointers = event.pointerCount
                    if (event.pointerCount == 2) initialPinchDist = hypot(event.getX(0)-event.getX(1), event.getY(0)-event.getY(1))
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX; val dy = event.y - lastY
                    if (hypot(dx, dy) > 10) isMoving = true
                    when (event.pointerCount) {
                        1 -> service.moveCursor(dx * 2.2f, dy * 2.2f)
                        2 -> {
                            val dist = hypot(event.getX(0)-event.getX(1), event.getY(0)-event.getY(1))
                            if (Math.abs(dist - initialPinchDist) > 60) {
                                service.zoom(dist > initialPinchDist); initialPinchDist = dist
                            } else if (Math.abs(dx) > 50 && Math.abs(dy) < 30) {
                                service.swipeNav(dx > 0); lastX = event.x
                            } else { service.scroll(dy) }
                        }
                    }
                    lastX = event.x; lastY = event.y
                }
                MotionEvent.ACTION_UP -> { if (!isMoving) { if (maxPointers == 1) service.performClick(false) else if (maxPointers == 2) service.performClick(true) } }
            }
            true
        }
    }

    private fun showHelp() {
        AlertDialog.Builder(this).setTitle("Touchpad Gestures")
            .setMessage("1 Finger: Move\n1 Tap: Left Click\n2 Finger Tap: Right Click\n2 Finger Slide: Scroll\nPinch: Zoom\n2 Finger Swipe: Back/Forward")
            .setPositiveButton("OK", null).show()
    }
}
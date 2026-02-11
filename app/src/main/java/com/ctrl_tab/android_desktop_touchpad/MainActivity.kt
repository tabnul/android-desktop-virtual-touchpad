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
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    private var lastX = 0f
    private var lastY = 0f
    private var isMoving = false

    override fun onResume() {
        super.onResume()
        // Forceer her-scan van schermen bij het openen van de app
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
            setPadding(50, 50, 50, 50)
            setBackgroundColor(Color.parseColor("#AAFFFFFF"))
        }

        val btnAccess = Button(this).apply {
            text = "Accessibility Settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        // Handmatige "Refresh Cursor" knop voor noodgevallen
        val btnRefresh = Button(this).apply {
            text = "Force Refresh Cursor"
            setOnClickListener { CursorService.instance?.updateDisplayAndCursor() }
        }

        controls.addView(btnAccess)
        controls.addView(btnRefresh)
        root.addView(touchpad)
        root.addView(controls, FrameLayout.LayoutParams(-1, -2))
        setContentView(root)

        touchpad.setOnTouchListener { _, event ->
            val service = CursorService.instance ?: return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x; lastY = event.y
                    isMoving = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.x - lastX) * 2.2f
                    val dy = (event.y - lastY) * 2.2f
                    if (hypot(dx, dy) > 5) isMoving = true
                    service.moveCursor(dx, dy)
                    lastX = event.x; lastY = event.y
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoving) service.performClick(false)
                }
            }
            true
        }
    }
}
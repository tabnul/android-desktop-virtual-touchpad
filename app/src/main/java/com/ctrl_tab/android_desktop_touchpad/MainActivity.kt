package com.ctrl_tab.android_desktop_touchpad

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.hypot
import android.accessibilityservice.AccessibilityService

class MainActivity : AppCompatActivity() {

    private var lastX = 0f
    private var lastY = 0f
    private var startX = 0f
    private var startY = 0f
    private var initialPinchDist = 0f
    private var isMoving = false
    private var maxPointers = 0
    private var touchSlop = 0f
    private var isCursorHidden = false

    private lateinit var btnSettings: ImageButton

    override fun onResume() {
        super.onResume()
        CursorService.instance?.updateDisplayAndCursor()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()

        // Prevent main screen from taking focus from desktop screen

        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }

        // Settings button
        btnSettings = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_preferences)
            setBackgroundColor(Color.parseColor("#44000000"))
            setPadding(20, 20, 20, 20)
            setOnClickListener { showConfigurationMenu() }
        }
        root.addView(btnSettings, FrameLayout.LayoutParams(120, 120).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(20, 20, 20, 20)
        })

        setContentView(root)

        root.setOnTouchListener { _, event ->
            if (isTouchInsideView(event, btnSettings)) {
                return@setOnTouchListener false
            }

            val service = CursorService.instance ?: return@setOnTouchListener false
            val sharedPref = getSharedPreferences("CursorSettings", Context.MODE_PRIVATE)
            val sensitivity = sharedPref.getFloat("cursor_sensitivity", 2.5f)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x; startY = event.y
                    lastX = event.x; lastY = event.y
                    isMoving = false; maxPointers = 1
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount > maxPointers) maxPointers = event.pointerCount
                    if (event.pointerCount == 2) initialPinchDist = calculateDist(event)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX
                    val dy = event.y - lastY

                    if (!isMoving && hypot(event.x - startX, event.y - startY) > touchSlop) {
                        isMoving = true
                    }

                    if (event.pointerCount == 1) {
                        service.moveCursor(dx * sensitivity, dy * sensitivity)
                    } else if (event.pointerCount == 2) {
                        handleMultiTouch(event, dy, service)
                    }
                    lastX = event.x; lastY = event.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isMoving) {
                        if (maxPointers == 1) service.performClick(false)
                        else if (maxPointers == 2) service.performClick(true)
                    }
                    maxPointers = 0; isMoving = false
                }
            }
            true
        }
        // --- Send to accessibility setting if service is not enabled ---
        if (!isAccessibilityServiceEnabled(this, CursorService::class.java)) {
            AlertDialog.Builder(this)
                .setTitle("Permissions required")
                .setMessage("To move the cursor on the external screen, you need to enable the 'Pixel Touchpad' accessibility service.")
                .setPositiveButton("Open settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun isTouchInsideView(event: MotionEvent, view: View): Boolean {
        val rect = Rect()
        view.getGlobalVisibleRect(rect)
        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun handleMultiTouch(e: MotionEvent, dy: Float, s: CursorService) {
        try {
            val dist = calculateDist(e)
            if (initialPinchDist > 0 && Math.abs(dist - initialPinchDist) > 80) {
                s.zoom(dist > initialPinchDist); initialPinchDist = dist
            } else if (Math.abs(dy) > 10) { s.scroll(dy) }
        } catch (ex: Exception) {}
    }

    private fun showConfigurationMenu() {
        val sharedPref = getSharedPreferences("CursorSettings", Context.MODE_PRIVATE)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 40, 50, 40) }

        // --- Size ---
        layout.addView(TextView(this).apply { text = "Cursor Size"; setTextColor(Color.BLACK) })
        layout.addView(SeekBar(this).apply {
            max = 150; progress = sharedPref.getInt("cursor_size", 40)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                    sharedPref.edit().putInt("cursor_size", p.coerceAtLeast(10)).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            setPadding(0, 10, 0, 30)
        })

        // --- Sensitivity ---
        layout.addView(TextView(this).apply { text = "Sensitivity"; setTextColor(Color.BLACK) })
        layout.addView(SeekBar(this).apply {
            max = 100; progress = (sharedPref.getFloat("cursor_sensitivity", 2.5f) * 10).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                    sharedPref.edit().putFloat("cursor_sensitivity", p.coerceAtLeast(5) / 10f).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            setPadding(0, 10, 0, 30)
        })

        // --- Colors ---
        layout.addView(TextView(this).apply { text = "Color"; setTextColor(Color.BLACK) })
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val colors = mapOf("Red" to Color.RED, "Blue" to Color.BLUE, "Green" to Color.GREEN, "White" to Color.WHITE)
        for ((name, col) in colors) {
            colorRow.addView(Button(this).apply {
                text = name; setBackgroundColor(Color.LTGRAY)
                setTextColor(if(col == Color.WHITE) Color.BLACK else col)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(5, 5, 5, 5) }
                setOnClickListener { sharedPref.edit().putInt("cursor_color", col).apply() }
            })
        }
        layout.addView(colorRow)

        // --- Actions ---
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 30, 0, 0) }
        btnRow.addView(Button(this).apply {
            text = if (isCursorHidden) "Show Cursor" else "Hide Cursor"
            setOnClickListener {
                isCursorHidden = !isCursorHidden
                CursorService.instance?.toggleCursor(isCursorHidden)
                text = if (isCursorHidden) "Hide Cursor" else "Hide Cursor"
            }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        btnRow.addView(Button(this).apply {
            text = "Permissions"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        layout.addView(btnRow)

        AlertDialog.Builder(this).setTitle("Settings").setView(layout).setPositiveButton("Close", null).show()
    }

    private fun calculateDist(e: MotionEvent) = try { hypot(e.getX(0)-e.getX(1), e.getY(0)-e.getY(1)) } catch(ex: Exception) { 0f }

    // --- Check for Accessibility Service ---
    private fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
        val expectedId = context.packageName + "/" + service.canonicalName
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(expectedId) ?: false
    }
}
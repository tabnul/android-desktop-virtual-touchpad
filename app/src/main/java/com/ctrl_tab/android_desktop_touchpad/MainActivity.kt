package com.ctrl_tab.android_desktop_touchpad

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.hypot

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

    private lateinit var hiddenInput: EditText
    private lateinit var btnSettings: ImageButton
    private lateinit var btnKeyboard: ImageButton

    override fun onResume() {
        super.onResume()
        CursorService.instance?.updateDisplayAndCursor()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()

        // Voorkom dat de activity focus steelt zodat het externe keyboard open kan blijven
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            isFocusable = false
            isFocusableInTouchMode = false
        }

        // Verborgen input voor keyboard focus
        hiddenInput = EditText(this).apply {
            layoutParams = FrameLayout.LayoutParams(1, 1)
            alpha = 0f
        }
        root.addView(hiddenInput)

        // 1. Keyboard-knop (Links boven)
        btnKeyboard = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            setBackgroundColor(Color.parseColor("#44000000"))
            setPadding(20, 20, 20, 20)
            setOnClickListener { toggleKeyboard() }
        }
        root.addView(btnKeyboard, FrameLayout.LayoutParams(120, 120).apply {
            gravity = Gravity.TOP or Gravity.START
            setMargins(20, 20, 20, 20)
        })

        // 2. Settings-knop (Rechts boven)
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
            // Voorkom touchpad actie als er op een knop wordt gedrukt
            if (isTouchInsideView(event, btnSettings) || isTouchInsideView(event, btnKeyboard)) {
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
    }

    private fun isTouchInsideView(event: MotionEvent, view: View): Boolean {
        val rect = Rect()
        view.getGlobalVisibleRect(rect)
        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun toggleKeyboard() {
        hiddenInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT)
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

        // --- Grootte ---
        layout.addView(TextView(this).apply { text = "Cursor Grootte" })
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

        // --- Gevoeligheid ---
        layout.addView(TextView(this).apply { text = "Gevoeligheid" })
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

        // --- Kleur ---
        layout.addView(TextView(this).apply { text = "Kleur" })
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

        // --- Systeem ---
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 30, 0, 0) }
        btnRow.addView(Button(this).apply {
            text = if (isCursorHidden) "Show" else "Hide"
            setOnClickListener {
                isCursorHidden = !isCursorHidden
                CursorService.instance?.toggleCursor(isCursorHidden)
                text = if (isCursorHidden) "Show" else "Hide"
            }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        btnRow.addView(Button(this).apply {
            text = "System"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        layout.addView(btnRow)

        AlertDialog.Builder(this).setTitle("Instellingen").setView(layout).setPositiveButton("Sluiten", null).show()
    }

    private fun calculateDist(e: MotionEvent) = try { hypot(e.getX(0)-e.getX(1), e.getY(0)-e.getY(1)) } catch(ex: Exception) { 0f }
}
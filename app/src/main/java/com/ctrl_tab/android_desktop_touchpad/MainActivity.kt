package com.ctrl_tab.android_desktop_touchpad

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val sharedPref = getSharedPreferences("CursorSettings", Context.MODE_PRIVATE)

        // --- Permissie Knoppen ---

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }

        // --- Cursor Instellingen ---

        val seekBarSize = findViewById<SeekBar>(R.id.seekBarSize)
        seekBarSize.progress = sharedPref.getInt("cursor_size", 30)
        seekBarSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = if (progress < 10) 10 else progress
                sharedPref.edit().putInt("cursor_size", size).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        findViewById<Button>(R.id.btnRed).setOnClickListener {
            sharedPref.edit().putInt("cursor_color", Color.RED).apply()
        }
        findViewById<Button>(R.id.btnBlue).setOnClickListener {
            sharedPref.edit().putInt("cursor_color", Color.BLUE).apply()
        }
        findViewById<Button>(R.id.btnGreen).setOnClickListener {
            sharedPref.edit().putInt("cursor_color", Color.GREEN).apply()
        }
    }
}
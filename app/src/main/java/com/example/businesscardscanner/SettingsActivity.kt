package com.example.businesscardscanner

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = com.example.businesscardscanner.databinding.ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.settingsBack.setOnClickListener {
            finish()
        }
    }
}

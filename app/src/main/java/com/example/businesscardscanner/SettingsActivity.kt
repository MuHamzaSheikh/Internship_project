package com.example.businesscardscanner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.businesscardscanner.databinding.ActivitySettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.settingsBack.setOnClickListener {
            finish()
        }

        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        binding.switchAutoResume.isChecked = prefs.getBoolean("auto_resume", true)

        binding.btnAutoResume.setOnClickListener {
            binding.switchAutoResume.isChecked = !binding.switchAutoResume.isChecked
        }

        binding.switchAutoResume.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_resume", isChecked).apply()
        }

        val languages = arrayOf("English", "Urdu", "Spanish", "French", "German", "Chinese")
        binding.btnLanguages.setOnClickListener {
            val currentLangIndex = prefs.getInt("selected_language", 0)
            MaterialAlertDialogBuilder(this)
                .setTitle("Select Language")
                .setSingleChoiceItems(languages, currentLangIndex) { dialog, which ->
                    prefs.edit().putInt("selected_language", which).apply()
                    Toast.makeText(this, "${languages[which]} selected", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .show()
        }

        binding.btnPrivacyPolicy.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://policies.google.com/privacy"))
            startActivity(intent)
        }

        binding.btnAbout.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("About")
                .setMessage("Business Card Scanner\nVersion 1.0\n\nA smart scanner app to save and manage business cards efficiently.")
                .setPositiveButton("OK", null)
                .show()
        }

        binding.btnShare.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Check out Business Card Scanner")
                putExtra(Intent.EXTRA_TEXT, "I've been using this great app to manage business cards. Download it here: https://play.google.com/store/apps/details?id=$packageName")
            }
            startActivity(Intent.createChooser(intent, "Share via"))
        }

        binding.btnRateUs.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
            }
        }
    }
}

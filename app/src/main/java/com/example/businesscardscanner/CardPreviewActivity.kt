package com.example.businesscardscanner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class CardPreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = com.example.businesscardscanner.databinding.ActivityCardPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.previewContainer, CardPreviewFragment.newInstance(intent.getStringExtra(EXTRA_CARD_ID)))
                .commit()
        }
    }

    companion object {
        private const val EXTRA_CARD_ID = "extra_card_id"

        fun createIntent(context: Context, cardId: String): Intent {
            return Intent(context, CardPreviewActivity::class.java).apply {
                putExtra(EXTRA_CARD_ID, cardId)
            }
        }
    }
}

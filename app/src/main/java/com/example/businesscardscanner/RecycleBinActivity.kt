package com.example.businesscardscanner

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.businesscardscanner.adapters.RecycleBinAdapter
import com.example.businesscardscanner.dialogs.PermanentDeleteDialog
import com.example.businesscardscanner.repository.BusinessCardRepository
import kotlinx.coroutines.launch

class RecycleBinActivity : AppCompatActivity() {
    private val repository by lazy { BusinessCardRepository.getInstance(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = com.example.businesscardscanner.databinding.ActivityRecycleBinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.binBack.setOnClickListener {
            finish()
        }

        val recyclerView = binding.recyclerRecycleBin
        recyclerView.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                repository.observeRecycleBin().collect { cards ->
                    recyclerView.adapter = RecycleBinAdapter(
                        cards,
                        onRestore = {
                            lifecycleScope.launch {
                                repository.restore(it)
                                Toast.makeText(this@RecycleBinActivity, "Restored", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDeletePermanent = { card ->
                            PermanentDeleteDialog(
                                this@RecycleBinActivity,
                                onCancel = {},
                                onDelete = {
                                    lifecycleScope.launch {
                                        runCatching {
                                            card.imageUri?.let { uri ->
                                                contentResolver.delete(uri, null, null)
                                            }
                                        }
                                        repository.deletePermanently(card)
                                        Toast.makeText(this@RecycleBinActivity, "Deleted permanently", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ).show()
                        }
                    )
                }
            }
        }
    }
}

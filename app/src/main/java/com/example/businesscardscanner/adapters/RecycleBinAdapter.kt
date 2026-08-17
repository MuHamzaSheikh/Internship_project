package com.example.businesscardscanner.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.businesscardscanner.R
import com.example.businesscardscanner.databinding.ItemRecycleCardBinding
import com.example.businesscardscanner.models.BusinessCard
import coil.load

class RecycleBinAdapter(
    private val cards: List<BusinessCard>,
    private val onRestore: (BusinessCard) -> Unit,
    private val onDeletePermanent: (BusinessCard) -> Unit
) : RecyclerView.Adapter<RecycleBinAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemRecycleCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecycleCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val card = cards[position]
        with(holder.binding) {
            imgRecycleCard.load(card.imageUri) {
                placeholder(R.drawable.card_reference)
                error(R.drawable.card_reference)
            }
            tvRecycleName.text = card.name
            tvRecycleRole.text = card.jobTitle.ifBlank { card.company }
            tvRecyclePhone.text = card.phone
            tvRecycleDate.text = "Deleted"
            tvRecycleCategory.text = card.group
            btnRestore.setOnClickListener { onRestore(card) }
            btnPermanentDelete.setOnClickListener { onDeletePermanent(card) }
        }
    }

    override fun getItemCount(): Int = cards.size
}

package com.example.businesscardscanner.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.businesscardscanner.R
import com.example.businesscardscanner.models.BusinessCard
import coil.load

class RecycleBinAdapter(
    private val cards: List<BusinessCard>,
    private val onRestore: (BusinessCard) -> Unit,
    private val onDeletePermanent: (BusinessCard) -> Unit
) : RecyclerView.Adapter<RecycleBinAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgCard: ImageView = view.findViewById(R.id.imgRecycleCard)
        val tvName: TextView = view.findViewById(R.id.tvRecycleName)
        val tvRole: TextView = view.findViewById(R.id.tvRecycleRole)
        val tvPhone: TextView = view.findViewById(R.id.tvRecyclePhone)
        val tvDate: TextView = view.findViewById(R.id.tvRecycleDate)
        val tvCategory: TextView = view.findViewById(R.id.tvRecycleCategory)
        val btnRestore: View = view.findViewById(R.id.btnRestore)
        val btnPermanent: View = view.findViewById(R.id.btnPermanentDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recycle_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val card = cards[position]
        holder.imgCard.load(card.imageUri) {
            placeholder(R.drawable.card_reference)
            error(R.drawable.card_reference)
        }
        holder.tvName.text = card.name
        holder.tvRole.text = card.jobTitle.ifBlank { card.company }
        holder.tvPhone.text = card.phone
        holder.tvDate.text = "Deleted"
        holder.tvCategory.text = card.group
        holder.btnRestore.setOnClickListener { onRestore(card) }
        holder.btnPermanent.setOnClickListener { onDeletePermanent(card) }
    }

    override fun getItemCount(): Int = cards.size
}

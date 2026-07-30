package com.example.businesscardscanner.adapters

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.businesscardscanner.R
import com.example.businesscardscanner.dialogs.DeleteCardDialog
import com.example.businesscardscanner.models.BusinessCard
import com.example.businesscardscanner.utils.ImageAspectRatioUtils
import coil.load

class BusinessCardAdapter(
    private var cards: List<BusinessCard>,
    private val onEdit: (BusinessCard) -> Unit,
    private val onShare: (BusinessCard) -> Unit,
    private val onSave: (BusinessCard) -> Unit,
    private val onDelete: (BusinessCard) -> Unit
) : RecyclerView.Adapter<BusinessCardAdapter.CardViewHolder>() {

    private val aspectRatioCache = mutableMapOf<String, Float>()

    class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgCard: ImageView = view.findViewById(R.id.imgCard)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvRole: TextView = view.findViewById(R.id.tvRole)
        val tvPhone: TextView = view.findViewById(R.id.tvPhone)
        val tvCategory: TextView = view.findViewById(R.id.tvCategory)
        val btnMore: ImageView = view.findViewById(R.id.btnMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_business_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val card = cards[position]
        val ratio = aspectRatioCache[card.id]
            ?: card.imageUri?.let { ImageAspectRatioUtils.getAspectRatio(holder.itemView.context, it) }
            ?: 1f
        aspectRatioCache[card.id] = ratio
        applyAspectRatio(holder.imgCard, ratio)
        holder.imgCard.load(card.imageUri) {
            placeholder(R.drawable.card_reference)
            error(R.drawable.card_reference)
            crossfade(true)
        }
        holder.tvName.text = card.name
        holder.tvRole.text = card.jobTitle.ifBlank { card.company }
        holder.tvPhone.text = card.phone
        holder.tvCategory.text = card.group

        holder.btnMore.setOnClickListener {
            showPopupMenu(it, holder.itemView.context, card)
        }
    }

    override fun getItemCount(): Int = cards.size

    fun updateItems(newCards: List<BusinessCard>) {
        cards = newCards
        notifyDataSetChanged()
    }

    private fun applyAspectRatio(view: ImageView, aspectRatio: Float) {
        val params = view.layoutParams as? ConstraintLayout.LayoutParams ?: return
        params.dimensionRatio = ImageAspectRatioUtils.toConstraintRatio(aspectRatio)
        view.layoutParams = params
    }

    private fun showPopupMenu(anchor: View, context: Context, card: BusinessCard) {
        val popupView = LayoutInflater.from(context).inflate(R.layout.layout_card_popup, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        popupView.findViewById<View>(R.id.actionSave).setOnClickListener {
            onSave(card)
            popupWindow.dismiss()
        }
        popupView.findViewById<View>(R.id.actionEdit).setOnClickListener {
            onEdit(card)
            popupWindow.dismiss()
        }
        popupView.findViewById<View>(R.id.actionDelete).setOnClickListener {
            DeleteCardDialog(context, onCancel = {}, onDelete = { onDelete(card) }).show()
            popupWindow.dismiss()
        }
        popupView.findViewById<View>(R.id.actionShare).setOnClickListener {
            onShare(card)
            popupWindow.dismiss()
        }
        popupWindow.showAsDropDown(anchor, -260, 12)
    }
}

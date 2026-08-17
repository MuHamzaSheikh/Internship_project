package com.example.businesscardscanner.adapters

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.recyclerview.widget.RecyclerView
import com.example.businesscardscanner.R
import com.example.businesscardscanner.databinding.ItemBusinessCardBinding
import com.example.businesscardscanner.databinding.LayoutCardPopupBinding

import com.example.businesscardscanner.models.BusinessCard
import coil.load

class BusinessCardAdapter(
    private var cards: List<BusinessCard>,
    private val onCardClick: (BusinessCard) -> Unit = {},
    private val onEdit: (BusinessCard) -> Unit,
    private val onShare: (BusinessCard) -> Unit,
    private val onSave: (BusinessCard) -> Unit,
    private val onDelete: (BusinessCard) -> Unit
) : RecyclerView.Adapter<BusinessCardAdapter.CardViewHolder>() {

    class CardViewHolder(val binding: ItemBusinessCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val binding = ItemBusinessCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val card = cards[position]
        with(holder.binding) {
            imgCard.load(card.imageUri) {
                placeholder(R.drawable.card_reference)
                error(R.drawable.card_reference)
                crossfade(true)
                transformations(coil.transform.CircleCropTransformation())
            }
            tvName.text = card.name.ifBlank { "Unknown" }
            tvRole.text = card.jobTitle.ifBlank { card.company }
            tvPhone.text = card.phone
            tvCategory.text = card.group

            btnMore.setOnClickListener {
                showPopupMenu(it, root.context, card)
            }
            root.setOnClickListener {
                onCardClick(card)
            }
        }
    }

    override fun getItemCount(): Int = cards.size

    fun updateItems(newCards: List<BusinessCard>) {
        cards = newCards
        notifyDataSetChanged()
    }

    private fun showPopupMenu(anchor: View, context: Context, card: BusinessCard) {
        val binding = LayoutCardPopupBinding.inflate(LayoutInflater.from(context))
        val popupWindow = PopupWindow(
            binding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        binding.actionSave.setOnClickListener {
            onSave(card)
            popupWindow.dismiss()
        }
        binding.actionEdit.setOnClickListener {
            onEdit(card)
            popupWindow.dismiss()
        }
        binding.actionDelete.setOnClickListener {
            onDelete(card)
            popupWindow.dismiss()
        }
        binding.actionShare.setOnClickListener {
            onShare(card)
            popupWindow.dismiss()
        }
        binding.root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val xOffset = -binding.root.measuredWidth + anchor.width
        popupWindow.showAsDropDown(anchor, xOffset, 0)
        
        val container = popupWindow.contentView.rootView
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val p = container.layoutParams as android.view.WindowManager.LayoutParams
        p.flags = p.flags or android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
        p.dimAmount = 0.5f
        wm.updateViewLayout(container, p)
    }
}

package com.example.businesscardscanner.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.businesscardscanner.R
import com.example.businesscardscanner.databinding.ItemCardImagePageBinding

class CardImagePagerAdapter(
    private var images: List<Uri?>,
    private val onImageClicked: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<CardImagePagerAdapter.ImageViewHolder>() {

    class ImageViewHolder(val binding: ItemCardImagePageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemCardImagePageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val uri = images.getOrNull(position)
        holder.binding.imgPage.load(uri ?: R.drawable.card_reference_vertical) {
            crossfade(true)
            memoryCachePolicy(coil.request.CachePolicy.DISABLED)
            diskCachePolicy(coil.request.CachePolicy.DISABLED)
        }
        
        holder.binding.imgPage.setOnClickListener {
            onImageClicked?.invoke(position)
        }
    }

    override fun getItemCount(): Int = images.size

    fun submitList(newImages: List<Uri?>) {
        images = newImages
        notifyDataSetChanged()
    }
}

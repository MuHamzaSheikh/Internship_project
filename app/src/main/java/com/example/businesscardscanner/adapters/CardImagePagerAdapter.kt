package com.example.businesscardscanner.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.businesscardscanner.R

class CardImagePagerAdapter(
    private var images: List<Uri?>
) : RecyclerView.Adapter<CardImagePagerAdapter.ImageViewHolder>() {

    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imgPage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_card_image_page, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val uri = images.getOrNull(position)
        holder.imageView.load(uri ?: R.drawable.card_reference_vertical) {
            crossfade(true)
        }
    }

    override fun getItemCount(): Int = images.size

    fun submitList(newImages: List<Uri?>) {
        images = newImages
        notifyDataSetChanged()
    }
}

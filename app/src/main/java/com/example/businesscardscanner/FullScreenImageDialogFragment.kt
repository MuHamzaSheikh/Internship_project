package com.example.businesscardscanner

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.example.businesscardscanner.views.ZoomableImageView

class FullScreenImageDialogFragment : DialogFragment() {

    private var images: List<Uri> = emptyList()
    private var startIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        
        arguments?.let {
            val uris = it.getStringArrayList(ARG_IMAGES) ?: emptyList()
            images = uris.map { uriString -> Uri.parse(uriString) }
            startIndex = it.getInt(ARG_START_INDEX, 0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_full_screen_image, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val pager = view.findViewById<ViewPager2>(R.id.fullScreenPager)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)

        pager.adapter = FullScreenPagerAdapter(images)
        pager.setCurrentItem(startIndex, false)

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    val currentView = (pager.getChildAt(0) as? RecyclerView)?.layoutManager?.findViewByPosition(pager.currentItem)
                    val zoomView = currentView?.findViewById<ZoomableImageView>(R.id.imgZoomablePage)
                    zoomView?.resetZoom()
                }
            }
        })

        btnClose.setOnClickListener {
            dismiss()
        }
    }

    private inner class FullScreenPagerAdapter(private val imageUris: List<Uri>) : RecyclerView.Adapter<FullScreenPagerAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val zoomView: ZoomableImageView = view.findViewById(R.id.imgZoomablePage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_zoomable_image_page, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.zoomView.load(imageUris[position]) {
                crossfade(true)
                memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                diskCachePolicy(coil.request.CachePolicy.DISABLED)
            }
            holder.zoomView.resetZoom()
        }

        override fun getItemCount(): Int = imageUris.size
    }

    companion object {
        private const val ARG_IMAGES = "arg_images"
        private const val ARG_START_INDEX = "arg_start_index"

        fun newInstance(images: List<Uri>, startIndex: Int = 0): FullScreenImageDialogFragment {
            return FullScreenImageDialogFragment().apply {
                arguments = Bundle().apply {
                    putStringArrayList(ARG_IMAGES, ArrayList(images.map { it.toString() }))
                    putInt(ARG_START_INDEX, startIndex)
                }
            }
        }
    }
}

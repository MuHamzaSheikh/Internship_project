package com.example.businesscardscanner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.businesscardscanner.adapters.CardImagePagerAdapter
import com.example.businesscardscanner.repository.BusinessCardRepository
import com.example.businesscardscanner.utils.ImageAspectRatioUtils
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayoutMediator
import coil.load
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class CardPreviewFragment : Fragment() {

    private var pagerAdapter: CardImagePagerAdapter? = null
    private var currentImages: List<android.net.Uri?> = emptyList()

    private var _binding: com.example.businesscardscanner.databinding.FragmentCardPreviewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
         _binding = com.example.businesscardscanner.databinding.FragmentCardPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repository = BusinessCardRepository.getInstance(requireContext().applicationContext)
        val cardId = arguments?.getString(ARG_CARD_ID)
        val pager = binding.previewPager
        val previewContainer = binding.previewImageContainer
        val back = binding.btnBack
        val edit = binding.btnEdit
        val share = binding.btnShare
        pagerAdapter = CardImagePagerAdapter(emptyList()) { position ->
            if (currentImages.isNotEmpty()) {
                val uris = currentImages.filterNotNull()
                if (uris.isNotEmpty()) {
                    val dialog = FullScreenImageDialogFragment.newInstance(uris, position)
                    dialog.show(parentFragmentManager, "FullScreenImageDialog")
                }
            }
        }
        pager.adapter = pagerAdapter

        fun updateDots(count: Int, position: Int) {
            val dots = binding.pageDots
            dots.visibility = if (count > 1) View.VISIBLE else View.GONE
        }

        (pager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView)?.isNestedScrollingEnabled = false

        fun applyPreviewAspect(uri: android.net.Uri?) {
            if (uri == null) return
            val ratio = ImageAspectRatioUtils.getAspectRatio(requireContext(), uri).takeIf { it > 0f } ?: 1f
            val widthPx = previewContainer.width.takeIf { it > 0 }
                ?: (resources.displayMetrics.widthPixels - (32 * resources.displayMetrics.density).roundToInt())
            val heightPx = (widthPx / ratio).roundToInt().coerceAtLeast((160 * resources.displayMetrics.density).roundToInt())
            
            if (previewContainer.layoutParams.height != heightPx) {
                previewContainer.layoutParams = previewContainer.layoutParams.apply { this.height = heightPx }
                previewContainer.requestLayout()
                pager.layoutParams = pager.layoutParams.apply { this.height = heightPx }
                pager.requestLayout()
            }
        }

        fun bindCard(cardIdValue: String) {
            viewLifecycleOwner.lifecycleScope.launch {
                repository.getById(cardIdValue)?.let { card ->
                    currentImages = listOfNotNull(card.imageUri, card.backImageUri)
                    pagerAdapter?.submitList(currentImages)
                    pager.setCurrentItem(0, false)
                    TabLayoutMediator(binding.pageDots, pager) { _, _ -> }.attach()
                    updateDots(currentImages.size, 0)
                    previewContainer.post { applyPreviewAspect(currentImages.firstOrNull()) }
                    binding.txtName.text = card.name.ifBlank { "-" }
                    binding.txtJob.text = card.jobTitle.ifBlank { "-" }
                    binding.txtCompany.text = card.company.ifBlank { "-" }
                    binding.txtPhone.text = card.phone.ifBlank { "-" }
                    binding.txtPhoneSecondary.text = card.phoneSecondary.ifBlank { "-" }
                    binding.txtEmail.text = card.email.ifBlank { "-" }
                    binding.txtAddress.text = card.address.ifBlank { "-" }
                    binding.txtGroup.text = card.group.ifBlank { "-" }
                    binding.txtDescription.text = card.description.ifBlank { "-" }
                    binding.txtNotes.text = card.notes.ifBlank { "-" }
                    
                    setupQuickActions(view, card)
                    setupShare(share, card)
                }
            }
        }

        back.setOnClickListener { parentFragmentManager.popBackStack() }
        edit.setOnClickListener {
            cardId?.let { startActivity(CardWorkflowActivity.createIntent(requireContext(), CardWorkflowActivity.StartStep.CARD_EDIT, it)) }
        }

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(currentImages.size, position)
                applyPreviewAspect(currentImages.getOrNull(position))
            }
        })

        cardId?.let { bindCard(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private fun setupShare(share: ImageView, card: com.example.businesscardscanner.models.BusinessCard) {
        share.setOnClickListener {
            val shareText = buildString {
                appendLine("Contact Details")
                if (card.name.isNotBlank()) appendLine("Name: ${card.name}")
                if (card.jobTitle.isNotBlank()) appendLine("Title: ${card.jobTitle}")
                if (card.company.isNotBlank()) appendLine("Company: ${card.company}")
                if (card.phone.isNotBlank()) appendLine("Phone: ${card.phone}")
                if (card.email.isNotBlank()) appendLine("Email: ${card.email}")
                if (card.address.isNotBlank()) appendLine("Address: ${card.address}")
                if (card.notes.isNotBlank()) appendLine("Notes: ${card.notes}")
            }
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Business Card: ${card.name}")
                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                card.imageUri?.let { uri ->
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    type = "image/jpeg"
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            startActivity(android.content.Intent.createChooser(intent, "Share Card via"))
        }
    }

    private fun setupQuickActions(view: View, card: com.example.businesscardscanner.models.BusinessCard) {
        val context = requireContext()
        binding.btnActionCallPreview.setOnClickListener {
            val number = card.phone.trim()
            if (number.isNotBlank()) {
                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number"))
                try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No dialer found", android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
        
        binding.btnActionSmsPreview.setOnClickListener {
            val number = card.phone.trim()
            if (number.isNotBlank()) {
                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:$number"))
                try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No messaging app found", android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
        
        binding.btnActionCallSecondaryPreview.setOnClickListener {
            val number = card.phoneSecondary.trim()
            if (number.isNotBlank()) {
                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number"))
                try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No dialer found", android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
        
        binding.btnActionSmsSecondaryPreview.setOnClickListener {
            val number = card.phoneSecondary.trim()
            if (number.isNotBlank()) {
                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:$number"))
                try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No messaging app found", android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
        
        binding.btnActionEmailPreview.setOnClickListener {
            val email = card.email.trim()
            if (email.isNotBlank()) {
                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:$email"))
                try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No email app found", android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
        
        binding.btnActionMapPreview.setOnClickListener {
            val address = card.address.trim()
            if (address.isNotBlank()) {
                val encoded = android.net.Uri.encode(address)
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=$encoded"))
                try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No maps app found", android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
        
        binding.btnSaveToContactsPreview.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                type = android.provider.ContactsContract.Contacts.CONTENT_TYPE
                putExtra(android.provider.ContactsContract.Intents.Insert.NAME, card.name.trim())
                putExtra(android.provider.ContactsContract.Intents.Insert.COMPANY, card.company.trim())
                putExtra(android.provider.ContactsContract.Intents.Insert.JOB_TITLE, card.jobTitle.trim())
                putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, card.phone.trim())
                putExtra(android.provider.ContactsContract.Intents.Insert.EMAIL, card.email.trim())
                putExtra(android.provider.ContactsContract.Intents.Insert.POSTAL, card.address.trim())
            }
            try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No contacts app found", android.widget.Toast.LENGTH_SHORT).show() }
        }
    }

    companion object {
        private const val ARG_CARD_ID = "extra_card_id"

        fun newInstance(cardId: String? = null) = CardPreviewFragment().apply {
            arguments = Bundle().apply {
                if (cardId != null) putString(ARG_CARD_ID, cardId)
            }
        }
    }
}

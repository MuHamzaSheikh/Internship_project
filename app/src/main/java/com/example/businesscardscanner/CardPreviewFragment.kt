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



        fun bindCard(cardIdValue: String) {
            viewLifecycleOwner.lifecycleScope.launch {
                repository.getById(cardIdValue)?.let { card ->
                    currentImages = listOfNotNull(card.imageUri, card.backImageUri)
                    pagerAdapter?.submitList(currentImages)
                    pager.setCurrentItem(0, false)
                    TabLayoutMediator(binding.pageDots, pager) { _, _ -> }.attach()
                    updateDots(currentImages.size, 0)
                    binding.txtName.text = card.name.ifBlank { "-" }
                    binding.txtJob.text = card.jobTitle.ifBlank { "-" }
                    binding.txtCompany.text = card.company.ifBlank { "-" }
                    binding.txtPhone.text = card.phone.ifBlank { "-" }
                    binding.txtPhoneSecondary.text = card.phoneSecondary.ifBlank { "-" }
                    binding.txtEmail.text = card.email.ifBlank { "-" }
                    binding.txtLocation.text = card.address.ifBlank { "-" }
                    binding.txtGroup.text = card.group.ifBlank { "-" }
                    binding.txtDescription.text = card.description.ifBlank { "-" }
                    binding.txtNotes.text = card.notes.ifBlank { "-" }
                    
                    setupQuickActions(view, card)
                    setupShare(share, card)
                }
            }
        }

        back.setOnClickListener { requireActivity().finish() }
        edit.setOnClickListener {
            cardId?.let { startActivity(CardWorkflowActivity.createIntent(requireContext(), CardWorkflowActivity.StartStep.CARD_EDIT, it)) }
        }

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(currentImages.size, position)
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
        val txtPhone = binding.txtPhone
        val txtPhoneSecondary = binding.txtPhoneSecondary
        val txtEmail = binding.txtEmail
        val txtLocation = binding.txtLocation
        
        fun setupPhonePopup(phoneText: TextView, phoneNumber: String) {
            phoneText.setOnTouchListener { v, event ->
                val rightDrawable = phoneText.compoundDrawables[2]
                if (rightDrawable != null && event.x >= (phoneText.width - phoneText.paddingRight - rightDrawable.bounds.width() - 40)) {
                    if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                        return@setOnTouchListener true
                    }
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        val popupBinding = com.example.businesscardscanner.databinding.LayoutPhonePopupBinding.inflate(LayoutInflater.from(context))
                        val popupWindow = android.widget.PopupWindow(
                            popupBinding.root,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            true
                        )
                        popupWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                        
                        popupBinding.actionCall.setOnClickListener {
                            val number = phoneNumber.trim()
                            if (number.isNotBlank()) {
                                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number"))
                                try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No dialer found", android.widget.Toast.LENGTH_SHORT).show() }
                            }
                            popupWindow.dismiss()
                        }
                        popupBinding.actionWhatsapp.setOnClickListener {
                            val number = phoneNumber.trim()
                            if (number.isNotBlank()) {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://api.whatsapp.com/send?phone=$number"))
                                try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "WhatsApp not found", android.widget.Toast.LENGTH_SHORT).show() }
                            }
                            popupWindow.dismiss()
                        }
                        popupBinding.actionMessenger.setOnClickListener {
                            val number = phoneNumber.trim()
                            if (number.isNotBlank()) {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:$number"))
                                try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "Messenger/SMS not found", android.widget.Toast.LENGTH_SHORT).show() }
                            }
                            popupWindow.dismiss()
                        }
                        
                        popupBinding.root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                        val xOffset = -popupBinding.root.measuredWidth + phoneText.width
                        popupWindow.showAsDropDown(phoneText, xOffset, 0)
                        v.performClick()
                        return@setOnTouchListener true
                    }
                }
                false
            }
        }
        
        setupPhonePopup(txtPhone, card.phone)
        setupPhonePopup(txtPhoneSecondary, card.phoneSecondary)
        
        txtEmail.setOnTouchListener { v, event ->
            val rightDrawable = txtEmail.compoundDrawables[2]
            if (rightDrawable != null && event.x >= (txtEmail.width - txtEmail.paddingRight - rightDrawable.bounds.width() - 40)) {
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    return@setOnTouchListener true
                }
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    val email = card.email.trim()
                    if (email.isNotBlank()) {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:$email"))
                        try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No email app found", android.widget.Toast.LENGTH_SHORT).show() }
                    }
                    v.performClick()
                    return@setOnTouchListener true
                }
            }
            false
        }
        
        txtLocation.setOnTouchListener { v, event ->
            val rightDrawable = txtLocation.compoundDrawables[2]
            if (rightDrawable != null && event.x >= (txtLocation.width - txtLocation.paddingRight - rightDrawable.bounds.width() - 40)) {
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    return@setOnTouchListener true
                }
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    val address = card.address.trim()
                    if (address.isNotBlank()) {
                        val encoded = android.net.Uri.encode(address)
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=$encoded"))
                        try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No maps app found", android.widget.Toast.LENGTH_SHORT).show() }
                    }
                    v.performClick()
                    return@setOnTouchListener true
                }
            }
            false
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

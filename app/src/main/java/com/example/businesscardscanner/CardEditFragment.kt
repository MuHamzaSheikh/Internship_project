package com.example.businesscardscanner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.businesscardscanner.adapters.CardImagePagerAdapter
import com.example.businesscardscanner.models.BusinessCard
import com.example.businesscardscanner.ocr.OcrField
import com.example.businesscardscanner.repository.BusinessCardRepository
import com.example.businesscardscanner.utils.ImageAspectRatioUtils
import com.example.businesscardscanner.viewmodel.CardFlowViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class CardEditFragment : Fragment() {

    private val flowViewModel: CardFlowViewModel by activityViewModels()
    private var cardImagePagerAdapter: CardImagePagerAdapter? = null
    private var existingCard: BusinessCard? = null
    private var currentImages: List<android.net.Uri?> = emptyList()

    interface FlowHost {
        fun onFinishFlow()
    }

    private var _binding: com.example.businesscardscanner.databinding.FragmentCardEditBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = com.example.businesscardscanner.databinding.FragmentCardEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repository = BusinessCardRepository.getInstance(requireContext().applicationContext)
        val state = flowViewModel.state.value
        val cardId = arguments?.getString(ARG_CARD_ID)
            ?: requireActivity().intent.getStringExtra(ARG_CARD_ID)
        val groupField = binding.etGroup
        val pager = binding.cardImagePager
        val previewContainer = binding.cardPreviewContainer
        cardImagePagerAdapter = CardImagePagerAdapter(emptyList())
        pager.adapter = cardImagePagerAdapter
        
        (pager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView)?.isNestedScrollingEnabled = false

        fun updateDots(count: Int, position: Int) {
            val dots = binding.pageDots
            dots.visibility = if (count > 1) View.VISIBLE else View.GONE
        }

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

        fun bindImages(images: List<android.net.Uri?>) {
            currentImages = images
            cardImagePagerAdapter?.submitList(images)
            pager.setCurrentItem(0, false)
            TabLayoutMediator(binding.pageDots, pager) { _, _ -> }.attach()
            updateDots(images.size, 0)
            previewContainer.post { applyPreviewAspect(images.firstOrNull()) }
        }

        groupField.setOnClickListener { groupField.showDropDown() }
        groupField.setOnItemClickListener { _, _, position, _ ->
            val value = groupField.adapter?.getItem(position)?.toString().orEmpty()
            when (value) {
                "+ Add new category" -> showAddCategoryDialog(groupField, repository)
                else -> if (value.isNotBlank()) groupField.setText(value, false)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repository.observeCategories().collectLatest { categories ->
                val options = (listOf("VIP", "Family", "Colleague", "Recent") + categories + "+ Add new category")
                    .distinct()
                groupField.setSimpleItems(options.toTypedArray())
                if (groupField.text.isNullOrBlank()) {
                    groupField.setText("VIP", false)
                }
            }
        }

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(currentImages.size, position)
                applyPreviewAspect(currentImages.getOrNull(position))
            }
        })

        if (cardId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                repository.getById(cardId)?.let { card ->
                    existingCard = card
                    bindCard(view, card)
                    bindImages(listOfNotNull(card.imageUri, card.backImageUri))
                    groupField.setText(card.group.ifBlank { "VIP" }, false)
                } ?: bindFromState(view, state, ::bindImages)
            }
        } else {
            bindFromState(view, state, ::bindImages)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            flowViewModel.state.collectLatest { currentState ->
                if (currentState.isProcessing) {
                    binding.ocrProgressBar.visibility = View.VISIBLE
                    binding.formBlock.alpha = 0.5f
                } else {
                    binding.ocrProgressBar.visibility = View.GONE
                    binding.formBlock.alpha = 1.0f
                    if (cardId == null && (currentState.frontOcrResult != null || currentState.backOcrResult != null || currentState.ocrResult != null)) {
                        bindFromState(view, currentState, ::bindImages)
                    }
                }
            }
        }
        
        setupQuickActions(view)

        binding.btnSaveCard.setOnClickListener {
            val selectedGroup = groupField.text?.toString().orEmpty().ifBlank { "VIP" }
            viewLifecycleOwner.lifecycleScope.launch {
                repository.saveCategory(selectedGroup)
                val card = existingCard?.copy(
                    group = binding.etGroup.text.toString(),
                    name = binding.etName.text.toString(),
                    jobTitle = binding.etJob.text.toString(),
                    company = binding.etCompany.text.toString(),
                    phone = binding.etPhone.text.toString(),
                    phoneSecondary = binding.etPhoneSecondary.text.toString(),
                    email = binding.etEmail.text.toString(),
                    address = binding.etAddress.text.toString(),
                    description = binding.etDescription.text.toString(),
                    notes = binding.etNotes.text.toString(),
                    updatedAt = System.currentTimeMillis()
                ) ?: BusinessCard(
                    id = cardId ?: state.pendingCard?.id ?: BusinessCard().id,
                    imageUri = currentImages.getOrNull(0) ?: state.frontImageUri ?: state.imageUri,
                    backImageUri = currentImages.getOrNull(1) ?: state.backImageUri,
                    name = binding.etName.text.toString(),
                    jobTitle = binding.etJob.text.toString(),
                    company = binding.etCompany.text.toString(),
                    phone = binding.etPhone.text.toString(),
                    phoneSecondary = binding.etPhoneSecondary.text.toString(),
                    email = binding.etEmail.text.toString(),
                    website = state.ocrResult?.website?.value.orEmpty(),
                    address = binding.etAddress.text.toString(),
                    description = binding.etDescription.text.toString(),
                    notes = binding.etNotes.text.toString(),
                    group = selectedGroup,
                    qrText = state.qrText,
                    qrTimestamp = state.qrTimestamp,
                    ocrText = state.ocrResult?.rawText.orEmpty(),
                    category = selectedGroup,
                    updatedAt = System.currentTimeMillis()
                )
                repository.upsert(card, category = selectedGroup)
                (activity as? FlowHost)?.onFinishFlow()
            }
        }

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }
    
    private fun setupQuickActions(view: View) {
        val context = requireContext()
        val etPhone = binding.etPhone
        val etEmail = binding.etEmail
        val etAddress = binding.etAddress
        val etName = binding.etName
        val etCompany = binding.etCompany
        val etJob = binding.etJob

        binding.btnActionCall.setOnClickListener {
            val number = etPhone.text.toString().trim()
            if (number.isNotBlank()) {
                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number"))
                try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No dialer found", android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
        
        binding.btnActionSms.setOnClickListener {
            val number = etPhone.text.toString().trim()
            if (number.isNotBlank()) {
                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:$number"))
                try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No messaging app found", android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
        
        binding.btnActionCallSecondary.setOnClickListener {
            val number = binding.etPhoneSecondary.text.toString().trim()
            if (number.isNotBlank()) {
                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number"))
                try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No dialer found", android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
        
        binding.btnActionSmsSecondary.setOnClickListener {
            val number = binding.etPhoneSecondary.text.toString().trim()
            if (number.isNotBlank()) {
                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:$number"))
                try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No messaging app found", android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
        
        binding.btnActionEmail.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isNotBlank()) {
                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:$email"))
                try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No email app found", android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
        
        binding.btnActionMap.setOnClickListener {
            val address = etAddress.text.toString().trim()
            if (address.isNotBlank()) {
                val encoded = android.net.Uri.encode(address)
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=$encoded"))
                try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No maps app found", android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
        
        binding.btnSaveToContacts.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                type = android.provider.ContactsContract.Contacts.CONTENT_TYPE
                putExtra(android.provider.ContactsContract.Intents.Insert.NAME, etName.text.toString().trim())
                putExtra(android.provider.ContactsContract.Intents.Insert.COMPANY, etCompany.text.toString().trim())
                putExtra(android.provider.ContactsContract.Intents.Insert.JOB_TITLE, etJob.text.toString().trim())
                putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, etPhone.text.toString().trim())
                putExtra(android.provider.ContactsContract.Intents.Insert.EMAIL, etEmail.text.toString().trim())
                putExtra(android.provider.ContactsContract.Intents.Insert.POSTAL, etAddress.text.toString().trim())
            }
            try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No contacts app found", android.widget.Toast.LENGTH_SHORT).show() }
        }
    }

    private fun bindFromState(
        view: View,
        state: com.example.businesscardscanner.viewmodel.CardFlowState,
        bindImages: (List<android.net.Uri?>) -> Unit
    ) {
        bindImages(listOfNotNull(state.frontImageUri ?: state.imageUri, state.backImageUri))
        val front = state.frontOcrResult ?: state.ocrResult
        val back = state.backOcrResult
        bindOcrField(binding.etGroup, pickField(front?.category, back?.category), defaultValue = "VIP")
        bindOcrField(binding.etName, pickField(front?.name, back?.name))
        bindOcrField(binding.etJob, pickField(front?.jobTitle, back?.jobTitle))
        bindOcrField(binding.etCompany, pickField(front?.company, back?.company))
        bindOcrField(binding.etPhone, pickField(front?.phone, back?.phone))
        bindOcrField(binding.etPhoneSecondary, pickField(front?.phoneSecondary, back?.phoneSecondary))
        bindOcrField(binding.etEmail, pickField(front?.email, back?.email))
        bindOcrField(binding.etAddress, pickField(front?.address, back?.address))
        bindOcrField(binding.etDescription, pickField(front?.description, back?.description))
    }

    private fun bindCard(view: View, card: BusinessCard) {
        binding.etGroup.setText(card.group.ifBlank { "VIP" }, false)
        binding.etName.setText(card.name)
        binding.etJob.setText(card.jobTitle)
        binding.etCompany.setText(card.company)
        binding.etPhone.setText(card.phone)
        binding.etPhoneSecondary.setText(card.phoneSecondary)
        binding.etEmail.setText(card.email)
        binding.etAddress.setText(card.address)
        binding.etDescription.setText(card.description)
        binding.etNotes.setText(card.notes)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun bindOcrField(editText: EditText, field: OcrField?, defaultValue: String? = null) {
        val current = editText.text?.toString().orEmpty()
        val value = field?.value.orEmpty().ifBlank { defaultValue.orEmpty() }
        if (current.isBlank() && value.isNotBlank()) {
            editText.setText(value)
        } else if (current.isBlank() && defaultValue != null) {
            editText.setText(defaultValue)
        }
    }

    private fun pickField(front: OcrField?, back: OcrField?): OcrField? {
        if (front == null) return back
        if (back == null) return front
        if (front.value.isNotBlank()) return front
        if (back.value.isNotBlank()) return back
        return if (front.confidence >= back.confidence) front else back
    }

    private fun showAddCategoryDialog(
        groupField: MaterialAutoCompleteTextView,
        repository: BusinessCardRepository
    ) {
        val input = TextInputEditText(requireContext()).apply {
            hint = "Category name"
            setText(groupField.text?.toString().orEmpty())
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add new category")
            .setView(input)
            .setPositiveButton("Add") { dialog, _ ->
                val newCategory = input.text?.toString().orEmpty().trim()
                if (newCategory.isNotBlank()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        repository.saveCategory(newCategory)
                        groupField.setText(newCategory, false)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        private const val ARG_CARD_ID = "extra_card_id"

        fun newInstance(cardId: String? = null) = CardEditFragment().apply {
            arguments = Bundle().apply {
                if (cardId != null) putString(ARG_CARD_ID, cardId)
            }
        }
    }
}

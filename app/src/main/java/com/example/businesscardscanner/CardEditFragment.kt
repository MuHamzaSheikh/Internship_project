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
        fun onRetakeRequested(isBackSide: Boolean)
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
            
            val isQrMode = images.isEmpty()
            binding.cardPreviewContainer.visibility = if (isQrMode) View.GONE else View.VISIBLE
            binding.pageDots.visibility = if (isQrMode) View.GONE else (if (images.size > 1) View.VISIBLE else View.GONE)
            
            if (isQrMode) View.GONE else View.VISIBLE
            
            
            if (isQrMode) {
                binding.btnSaveCard.setBackgroundResource(R.drawable.edit_text_outline)
                binding.btnSaveCard.setTextColor(ContextCompat.getColor(requireContext(), R.color.TextPrimary))
            } else {
                binding.btnSaveCard.setBackgroundResource(R.drawable.btn_skip)
            }
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
                    description = binding.etLocation.text.toString(),
                    updatedAt = System.currentTimeMillis()
                ) ?: BusinessCard(
                    id = cardId ?: state.pendingCard?.id ?: BusinessCard().id,
                    imageUri = currentImages.getOrNull(0) ?: state.frontImageUri ?: state.imageUri,
                    backImageUri = currentImages.getOrNull(1) ?: state.backImageUri,
                    name = binding.etName.text.toString(),
                    jobTitle = binding.etJob.text.toString(),
                    company = binding.etCompany.text.toString(),
                    phone = binding.etPhone.text.toString(),
                    description = binding.etLocation.text.toString(),
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
        
        binding.btnRetake.setOnClickListener {
            val isBackSide = pager.currentItem == 1
            (activity as? FlowHost)?.onRetakeRequested(isBackSide)
        }
    }
    
    private fun setupQuickActions(view: View) {
        val context = requireContext()
        val etPhone = binding.etPhone
        val etLocation = binding.etLocation
        
        etPhone.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                if (event.rawX >= (etPhone.right - etPhone.compoundDrawables[2].bounds.width() - 40)) {
                    val popupBinding = com.example.businesscardscanner.databinding.LayoutPhonePopupBinding.inflate(LayoutInflater.from(context))
                    val popupWindow = android.widget.PopupWindow(
                        popupBinding.root,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        true
                    )
                    popupWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                    
                    popupBinding.actionCall.setOnClickListener {
                        val number = etPhone.text.toString().trim()
                        if (number.isNotBlank()) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:"))
                            try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No dialer found", android.widget.Toast.LENGTH_SHORT).show() }
                        }
                        popupWindow.dismiss()
                    }
                    popupBinding.actionWhatsapp.setOnClickListener {
                        val number = etPhone.text.toString().trim()
                        if (number.isNotBlank()) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://api.whatsapp.com/send?phone="))
                            try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "WhatsApp not found", android.widget.Toast.LENGTH_SHORT).show() }
                        }
                        popupWindow.dismiss()
                    }
                    popupBinding.actionMessenger.setOnClickListener {
                        val number = etPhone.text.toString().trim()
                        if (number.isNotBlank()) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("fb-messenger://user-thread/"))
                            try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "Messenger not found", android.widget.Toast.LENGTH_SHORT).show() }
                        }
                        popupWindow.dismiss()
                    }
                    
                    popupBinding.root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                    val xOffset = -popupBinding.root.measuredWidth + etPhone.width
                    popupWindow.showAsDropDown(etPhone, xOffset, 0)
                    v.performClick()
                    return@setOnTouchListener true
                }
            }
            false
        }
        
        etLocation.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                if (event.rawX >= (etLocation.right - etLocation.compoundDrawables[2].bounds.width() - 40)) {
                    val address = etLocation.text.toString().trim()
                    if (address.isNotBlank()) {
                        val encoded = android.net.Uri.encode(address)
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q="))
                        try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No maps app found", android.widget.Toast.LENGTH_SHORT).show() }
                    }
                    v.performClick()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private suspend fun exportPdf() {
        val frontUri = currentImages.getOrNull(0) ?: flowViewModel.state.value.frontImageUri ?: flowViewModel.state.value.imageUri
        val backUri = currentImages.getOrNull(1) ?: flowViewModel.state.value.backImageUri
        if (frontUri == null) {
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(requireContext(), "No image to export", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }

        try {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val resolver = requireContext().contentResolver

            // Page 1: Front
            val frontBitmap = resolver.openInputStream(frontUri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
            if (frontBitmap != null) {
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(frontBitmap.width, frontBitmap.height, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                page.canvas.drawBitmap(frontBitmap, 0f, 0f, null)
                pdfDocument.finishPage(page)
            }

            // Page 2: Back
            if (backUri != null) {
                val backBitmap = resolver.openInputStream(backUri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                if (backBitmap != null) {
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(backBitmap.width, backBitmap.height, 2).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawBitmap(backBitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)
                }
            }

            val pdfFile = java.io.File(requireContext().cacheDir, "BusinessCard_${System.currentTimeMillis()}.pdf")
            java.io.FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            val pdfUri = androidx.core.content.FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", pdfFile)

            withContext(kotlinx.coroutines.Dispatchers.Main) {
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(android.content.Intent.EXTRA_STREAM, pdfUri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(android.content.Intent.createChooser(shareIntent, "Save or Share PDF"))
            }
        } catch (e: Exception) {
            android.util.Log.e("CardEditFragment", "PDF export failed", e)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(requireContext(), "Failed to export PDF", android.widget.Toast.LENGTH_SHORT).show()
            }
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
        bindOcrField(binding.etEmail, pickField(front?.email, back?.email))
        bindOcrField(binding.etAddress, pickField(front?.address, back?.address))
        bindOcrField(binding.etLocation, pickField(front?.description, back?.description))
    }

    private fun bindCard(view: View, card: BusinessCard) {
        binding.etGroup.setText(card.group.ifBlank { "VIP" }, false)
        binding.etName.setText(card.name)
        binding.etJob.setText(card.jobTitle)
        binding.etCompany.setText(card.company)
        binding.etPhone.setText(card.phone)
        binding.etEmail.setText(card.email)
        binding.etAddress.setText(card.address)
        binding.etLocation.setText(card.description)
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

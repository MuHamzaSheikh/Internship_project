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
        fun onRetakeRequested(side: com.example.businesscardscanner.viewmodel.CaptureSide, retakeBoth: Boolean)
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



        fun bindImages(images: List<android.net.Uri?>) {
            currentImages = images
            cardImagePagerAdapter?.submitList(images)
            pager.setCurrentItem(0, false)
            TabLayoutMediator(binding.pageDots, pager) { _, _ -> }.attach()
            updateDots(images.size, 0)
            
            val isQrMode = images.isEmpty()
            binding.cardPreviewContainer.visibility = if (isQrMode) View.GONE else View.VISIBLE
            binding.pageDots.visibility = if (isQrMode) View.GONE else (if (images.size > 1) View.VISIBLE else View.GONE)
            
            val shouldShowRetake = !isQrMode && cardId == null
            binding.btnRetake.visibility = if (shouldShowRetake) View.VISIBLE else View.GONE
        }

        var groupMenu: android.widget.PopupMenu? = null
        
        groupField.setOnClickListener {
            groupMenu?.show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repository.observeCategories().collectLatest { categories ->
                val options = (listOf("VIP", "Family", "Colleague", "Recent") + categories + "+ Add new category").distinct()
                
                groupMenu = android.widget.PopupMenu(requireContext(), groupField, android.view.Gravity.END).apply {
                    options.forEachIndexed { index, option ->
                        menu.add(android.view.Menu.NONE, index, index, option)
                    }
                    setOnMenuItemClickListener { item ->
                        val value = options[item.itemId]
                        if (value == "+ Add new category") {
                            groupField.setText("")
                            showAddCategoryDialog(groupField, repository)
                        } else if (value.isNotBlank()) {
                            groupField.setText(value)
                        }
                        true
                    }
                }
                
                if (groupField.text.isNullOrBlank()) {
                    groupField.setText("VIP")
                }
            }
        }

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(currentImages.size, position)
            }
        })

        if (cardId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                repository.getById(cardId)?.let { card ->
                    existingCard = card
                    bindCard(view, card)
                    bindImages(listOfNotNull(card.imageUri, card.backImageUri))
                    groupField.setText(card.group.ifBlank { "VIP" })
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
            var selectedGroup = groupField.text?.toString().orEmpty().trim()
            if (selectedGroup.isBlank() || selectedGroup.equals("+ Add new category", ignoreCase = true)) {
                selectedGroup = "VIP"
            }
            viewLifecycleOwner.lifecycleScope.launch {
                repository.saveCategory(selectedGroup)
                val card = existingCard?.copy(
                    group = binding.etGroup.text.toString(),
                    name = binding.etName.text.toString(),
                    jobTitle = binding.etJob.text.toString(),
                    company = binding.etCompany.text.toString(),
                    phone = binding.etPhone.text.toString(),
                    phoneSecondary = binding.etPhoneSecondary.text.toString(),
                    description = binding.etDescription.text.toString(),
                    address = binding.etLocation.text.toString(),
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
                    description = binding.etDescription.text.toString(),
                    address = binding.etLocation.text.toString(),
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
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        
        binding.btnRetake.setOnClickListener {
            val options = arrayOf("Retake Front Side", "Retake Back Side", "Retake Both Sides")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Retake Picture")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> (activity as? FlowHost)?.onRetakeRequested(com.example.businesscardscanner.viewmodel.CaptureSide.FRONT, false)
                        1 -> (activity as? FlowHost)?.onRetakeRequested(com.example.businesscardscanner.viewmodel.CaptureSide.BACK, false)
                        2 -> (activity as? FlowHost)?.onRetakeRequested(com.example.businesscardscanner.viewmodel.CaptureSide.FRONT, true)
                    }
                }
                .show()
        }
    }
    
    private fun setupQuickActions(view: View) {
        val context = requireContext()
        val etPhone = binding.etPhone
        val etPhoneSecondary = binding.etPhoneSecondary
        val etEmail = binding.etEmail
        val etLocation = binding.etLocation
        
        fun setupPhonePopup(phoneField: EditText) {
            phoneField.setOnTouchListener { v, event ->
                val rightDrawable = phoneField.compoundDrawables[2]
                if (rightDrawable != null && event.x >= (phoneField.width - phoneField.paddingRight - rightDrawable.bounds.width() - 40)) {
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
                            val number = phoneField.text.toString().trim()
                            if (number.isNotBlank()) {
                                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number"))
                                try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No dialer found", android.widget.Toast.LENGTH_SHORT).show() }
                            }
                            popupWindow.dismiss()
                        }
                        popupBinding.actionWhatsapp.setOnClickListener {
                            val number = phoneField.text.toString().trim()
                            if (number.isNotBlank()) {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://api.whatsapp.com/send?phone=$number"))
                                try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "WhatsApp not found", android.widget.Toast.LENGTH_SHORT).show() }
                            }
                            popupWindow.dismiss()
                        }
                        popupBinding.actionMessenger.setOnClickListener {
                            val number = phoneField.text.toString().trim()
                            if (number.isNotBlank()) {
                                // Fallback to SMS if messenger not specifically supported via intent easily
                                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:$number"))
                                try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "Messenger/SMS not found", android.widget.Toast.LENGTH_SHORT).show() }
                            }
                            popupWindow.dismiss()
                        }
                        
                        popupBinding.root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                        val xOffset = -popupBinding.root.measuredWidth + phoneField.width
                        popupWindow.showAsDropDown(phoneField, xOffset, 0)
                        v.performClick()
                        return@setOnTouchListener true
                    }
                }
                false
            }
        }
        
        setupPhonePopup(etPhone)
        setupPhonePopup(etPhoneSecondary)
        
        etEmail.setOnTouchListener { v, event ->
            val rightDrawable = etEmail.compoundDrawables[2]
            if (rightDrawable != null && event.x >= (etEmail.width - etEmail.paddingRight - rightDrawable.bounds.width() - 40)) {
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    return@setOnTouchListener true
                }
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    val email = etEmail.text.toString().trim()
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
        
        etLocation.setOnTouchListener { v, event ->
            val rightDrawable = etLocation.compoundDrawables[2]
            if (rightDrawable != null && event.x >= (etLocation.width - etLocation.paddingRight - rightDrawable.bounds.width() - 40)) {
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    return@setOnTouchListener true
                }
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    val address = etLocation.text.toString().trim()
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
        bindOcrField(binding.etLocation, pickField(front?.address, back?.address))
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
        binding.etLocation.setText(card.address)
        binding.etDescription.setText(card.description)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun bindOcrField(textView: android.widget.TextView, field: OcrField?, defaultValue: String? = null) {
        val current = textView.text?.toString().orEmpty()
        val value = field?.value.orEmpty().ifBlank { defaultValue.orEmpty() }
        if (current.isBlank() && value.isNotBlank()) {
            textView.setText(value)
        } else if (current.isBlank() && defaultValue != null) {
            textView.setText(defaultValue)
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
        groupField: android.widget.TextView,
        repository: BusinessCardRepository
    ) {
        val input = TextInputEditText(requireContext()).apply {
            hint = "Category name"
            val currentText = groupField.text?.toString().orEmpty()
            if (!currentText.equals("+ Add new category", ignoreCase = true)) {
                setText(currentText)
            }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add new category")
            .setView(input)
            .setPositiveButton("Add") { dialog, _ ->
                val newCategory = input.text?.toString().orEmpty().trim()
                if (newCategory.isNotBlank()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        repository.saveCategory(newCategory)
                        groupField.setText(newCategory)
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

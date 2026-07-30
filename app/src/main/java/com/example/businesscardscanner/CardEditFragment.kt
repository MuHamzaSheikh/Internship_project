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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class CardEditFragment : Fragment() {

    private val flowViewModel: CardFlowViewModel by activityViewModels()
    private var cardImagePagerAdapter: CardImagePagerAdapter? = null
    private var existingCard: BusinessCard? = null
    private var currentImages: List<android.net.Uri?> = emptyList()

    interface FlowHost {
        fun onFinishFlow()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_card_edit, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repository = BusinessCardRepository.getInstance(requireContext().applicationContext)
        val state = flowViewModel.state.value
        val cardId = arguments?.getString(ARG_CARD_ID)
            ?: requireActivity().intent.getStringExtra(ARG_CARD_ID)
        val groupField = view.findViewById<MaterialAutoCompleteTextView>(R.id.etGroup)
        val pager = view.findViewById<ViewPager2>(R.id.cardImagePager)
        val previewContainer = view.findViewById<MaterialCardView>(R.id.cardPreviewContainer)
        cardImagePagerAdapter = CardImagePagerAdapter(emptyList())
        pager.adapter = cardImagePagerAdapter

        fun updateDots(count: Int, position: Int) {
            val dots = view.findViewById<android.widget.TextView>(R.id.pageDots)
            dots.visibility = if (count > 1) View.VISIBLE else View.GONE
            dots.text = if (count > 1) if (position == 0) "--  *" else "*  --" else ""
        }

        fun applyPreviewAspect(uri: android.net.Uri?) {
            if (uri == null) return
            val ratio = ImageAspectRatioUtils.getAspectRatio(requireContext(), uri).takeIf { it > 0f } ?: 1f
            val widthPx = previewContainer.width.takeIf { it > 0 }
                ?: (resources.displayMetrics.widthPixels - (40 * resources.displayMetrics.density).roundToInt())
            val heightPx = if (ratio >= 1f) {
                (widthPx / ratio).roundToInt()
            } else {
                (widthPx / ratio).roundToInt()
            }.coerceAtLeast((160 * resources.displayMetrics.density).roundToInt())
            previewContainer.layoutParams = previewContainer.layoutParams.apply { this.height = heightPx }
            previewContainer.requestLayout()
            pager.layoutParams = pager.layoutParams.apply { this.height = heightPx }
            pager.requestLayout()
        }

        fun bindImages(images: List<android.net.Uri?>) {
            currentImages = images
            cardImagePagerAdapter?.submitList(images)
            pager.setCurrentItem(0, false)
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

        view.findViewById<View>(R.id.btnSaveCard).setOnClickListener {
            val selectedGroup = groupField.text?.toString().orEmpty().ifBlank { "VIP" }
            viewLifecycleOwner.lifecycleScope.launch {
                repository.saveCategory(selectedGroup)
                val current = existingCard
                val card = BusinessCard(
                    id = cardId ?: state.pendingCard?.id ?: BusinessCard().id,
                    imageUri = current?.imageUri ?: state.frontImageUri ?: state.imageUri,
                    backImageUri = current?.backImageUri ?: state.backImageUri,
                    name = view.findViewById<EditText>(R.id.etName).text.toString(),
                    jobTitle = view.findViewById<EditText>(R.id.etJob).text.toString(),
                    company = view.findViewById<EditText>(R.id.etCompany).text.toString(),
                    phone = view.findViewById<EditText>(R.id.etPhone).text.toString(),
                    email = view.findViewById<EditText>(R.id.etEmail).text.toString(),
                    website = state.ocrResult?.website?.value.orEmpty(),
                    address = view.findViewById<EditText>(R.id.etAddress).text.toString(),
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

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
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
        bindOcrField(view.findViewById(R.id.etGroup), pickField(front?.category, back?.category), defaultValue = "VIP")
        bindOcrField(view.findViewById(R.id.etName), pickField(front?.name, back?.name))
        bindOcrField(view.findViewById(R.id.etJob), pickField(front?.jobTitle, back?.jobTitle))
        bindOcrField(view.findViewById(R.id.etCompany), pickField(front?.company, back?.company))
        bindOcrField(view.findViewById(R.id.etPhone), pickField(front?.phone, back?.phone))
        bindOcrField(view.findViewById(R.id.etEmail), pickField(front?.email, back?.email))
        bindOcrField(view.findViewById(R.id.etAddress), pickField(front?.address, back?.address))
    }

    private fun bindCard(view: View, card: BusinessCard) {
        view.findViewById<EditText>(R.id.etGroup).setText(card.group.ifBlank { "VIP" })
        view.findViewById<EditText>(R.id.etName).setText(card.name)
        view.findViewById<EditText>(R.id.etJob).setText(card.jobTitle)
        view.findViewById<EditText>(R.id.etCompany).setText(card.company)
        view.findViewById<EditText>(R.id.etPhone).setText(card.phone)
        view.findViewById<EditText>(R.id.etEmail).setText(card.email)
        view.findViewById<EditText>(R.id.etAddress).setText(card.address)
    }

    private fun bindOcrField(editText: EditText, field: OcrField?, defaultValue: String? = null) {
        val current = editText.text?.toString().orEmpty()
        val value = field?.value.orEmpty().ifBlank { defaultValue.orEmpty() }
        if (current.isBlank() && value.isNotBlank()) {
            editText.setText(value)
        } else if (current.isBlank() && defaultValue != null) {
            editText.setText(defaultValue)
        }
        if ((field?.confidence ?: 100) < 70 && field?.value?.isNotBlank() == true) {
            editText.backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.holo_orange_light)
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

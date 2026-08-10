package com.example.businesscardscanner.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.businesscardscanner.models.BusinessCard
import com.example.businesscardscanner.ocr.OcrResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CardFlowState(
    val qrText: String? = null,
    val qrTimestamp: Long? = null,
    val rawImageUri: Uri? = null,
    val imageUri: Uri? = null,
    val ocrUri: Uri? = null,
    val frontImageUri: Uri? = null,
    val backImageUri: Uri? = null,
    val ocrResult: OcrResult? = null,
    val frontOcrResult: OcrResult? = null,
    val backOcrResult: OcrResult? = null,
    val pendingCard: BusinessCard? = null,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val captureMode: CaptureMode = CaptureMode.CARD,
    val captureSide: CaptureSide = CaptureSide.FRONT
) {
    /**
     * DEDICATED OCR SOURCE:
     * Represents the exact image OCR should read, decoupled from display/filter previews.
     * Any future feature touching image processing must NOT redirect this function's output without explicit review.
     */
    fun getOcrSourceImage(): Uri? {
        return ocrUri ?: imageUri
    }
}

class CardFlowViewModel : ViewModel() {
    private val _state = MutableStateFlow(CardFlowState())
    val state: StateFlow<CardFlowState> = _state.asStateFlow()

    fun setQrResult(text: String) {
        _state.value = _state.value.copy(qrText = text, qrTimestamp = System.currentTimeMillis())
    }

    fun setRawImageUri(uri: Uri) {
        _state.value = _state.value.copy(rawImageUri = uri)
    }

    fun setImageUri(uri: Uri, ocrUri: Uri? = null) {
        val finalOcrUri = ocrUri ?: uri
        when (_state.value.captureSide) {
            CaptureSide.FRONT -> _state.value = _state.value.copy(imageUri = uri, ocrUri = finalOcrUri, frontImageUri = uri)
            CaptureSide.BACK -> _state.value = _state.value.copy(imageUri = uri, ocrUri = finalOcrUri, backImageUri = uri)
        }
    }

    fun setCaptureSide(side: CaptureSide) {
        _state.value = _state.value.copy(captureSide = side)
    }

    fun setOcrResult(result: OcrResult) {
        val updated = when (_state.value.captureSide) {
            CaptureSide.FRONT -> _state.value.copy(frontOcrResult = result)
            CaptureSide.BACK -> _state.value.copy(backOcrResult = result)
        }
        _state.value = updated.copy(
            ocrResult = mergeOcrResults(updated.frontOcrResult, updated.backOcrResult),
            isProcessing = false,
            errorMessage = null
        )
    }

    fun setPendingCard(card: BusinessCard?) {
        _state.value = _state.value.copy(pendingCard = card)
    }

    fun setProcessing(isProcessing: Boolean) {
        _state.value = _state.value.copy(isProcessing = isProcessing, errorMessage = null)
    }

    fun setError(message: String?) {
        _state.value = _state.value.copy(isProcessing = false, errorMessage = message)
    }

    fun setCaptureMode(mode: CaptureMode) {
        _state.value = _state.value.copy(captureMode = mode)
    }

    private fun mergeOcrResults(front: OcrResult?, back: OcrResult?): OcrResult? {
        if (front == null && back == null) return null
        if (front == null) return back
        if (back == null) return front

        val rejectedBackFields = mutableListOf<String>()

        fun pick(
            frontField: com.example.businesscardscanner.ocr.OcrField,
            backField: com.example.businesscardscanner.ocr.OcrField,
            trackReject: Boolean = false
        ): com.example.businesscardscanner.ocr.OcrField {
            return when {
                frontField.value.isNotBlank() -> {
                    if (trackReject && backField.value.isNotBlank() && backField.value.lowercase() != frontField.value.lowercase()) {
                        rejectedBackFields.add(backField.value)
                    }
                    frontField
                }
                backField.value.isNotBlank() -> backField
                frontField.confidence >= backField.confidence -> frontField
                else -> backField
            }
        }
        
        val mergedName = pick(front.name, back.name, trackReject = true)
        val mergedCompany = pick(front.company, back.company, trackReject = true)
        val mergedJobTitle = pick(front.jobTitle, back.jobTitle, trackReject = true)
        // We don't track rejects for regex-based fields (phone, email) as per user request to keep description clean of them
        val mergedPhone = pick(front.phone, back.phone)
        val mergedPhoneSecondary = pick(front.phoneSecondary, back.phoneSecondary)
        val mergedEmail = pick(front.email, back.email)
        val mergedWebsite = pick(front.website, back.website)
        val mergedAddress = pick(front.address, back.address, trackReject = true)
        val mergedCategory = pick(front.category, back.category)
        val mergedSocial = pick(front.social, back.social)

        val allDescriptions = mutableListOf<String>()
        if (front.description.value.isNotBlank()) allDescriptions.add(front.description.value)
        if (back.description.value.isNotBlank()) allDescriptions.add(back.description.value)
        
        if (rejectedBackFields.isNotEmpty()) {
            allDescriptions.add(rejectedBackFields.joinToString("\n"))
        }

        val combinedDescription = allDescriptions
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .trim()

        return front.copy(
            name = mergedName,
            company = mergedCompany,
            jobTitle = mergedJobTitle,
            phone = mergedPhone,
            phoneSecondary = mergedPhoneSecondary,
            email = mergedEmail,
            website = mergedWebsite,
            address = mergedAddress,
            category = mergedCategory,
            social = mergedSocial,
            description = com.example.businesscardscanner.ocr.OcrField(combinedDescription, 80),
            rawText = listOf(front.rawText, back.rawText).filter { it.isNotBlank() }.joinToString("\n")
        )
    }
}

enum class CaptureSide {
    FRONT,
    BACK
}

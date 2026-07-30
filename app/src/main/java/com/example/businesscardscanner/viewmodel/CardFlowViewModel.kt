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
    val imageUri: Uri? = null,
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
)

class CardFlowViewModel : ViewModel() {
    private val _state = MutableStateFlow(CardFlowState())
    val state: StateFlow<CardFlowState> = _state.asStateFlow()

    fun setQrResult(text: String) {
        _state.value = _state.value.copy(qrText = text, qrTimestamp = System.currentTimeMillis())
    }

    fun setImageUri(uri: Uri) {
        when (_state.value.captureSide) {
            CaptureSide.FRONT -> _state.value = _state.value.copy(imageUri = uri, frontImageUri = uri)
            CaptureSide.BACK -> _state.value = _state.value.copy(imageUri = uri, backImageUri = uri)
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

        fun pick(frontField: com.example.businesscardscanner.ocr.OcrField, backField: com.example.businesscardscanner.ocr.OcrField): com.example.businesscardscanner.ocr.OcrField {
            return when {
                frontField.value.isNotBlank() -> frontField
                backField.value.isNotBlank() -> backField
                frontField.confidence >= backField.confidence -> frontField
                else -> backField
            }
        }

        return front.copy(
            name = pick(front.name, back.name),
            company = pick(front.company, back.company),
            jobTitle = pick(front.jobTitle, back.jobTitle),
            phone = pick(front.phone, back.phone),
            email = pick(front.email, back.email),
            website = pick(front.website, back.website),
            address = pick(front.address, back.address),
            category = pick(front.category, back.category),
            social = pick(front.social, back.social),
            rawText = listOf(front.rawText, back.rawText).filter { it.isNotBlank() }.joinToString("\n")
        )
    }
}

enum class CaptureSide {
    FRONT,
    BACK
}

package com.example.businesscardscanner.ocr

data class OcrField(
    val value: String = "",
    val confidence: Int = 0
)

data class RecognizedTextBlock(
    val text: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val lineIndex: Int = 0
)

data class OcrResult(
    val rawText: String = "",
    val name: OcrField = OcrField(),
    val company: OcrField = OcrField(),
    val jobTitle: OcrField = OcrField(),
    val phone: OcrField = OcrField(),
    val email: OcrField = OcrField(),
    val website: OcrField = OcrField(),
    val address: OcrField = OcrField(),
    val social: OcrField = OcrField(),
    val category: OcrField = OcrField(),
    val qrText: String? = null,
    val qrTimestamp: Long? = null,
    val blocks: List<RecognizedTextBlock> = emptyList(),
    val parsedValues: Map<String, String> = emptyMap()
)

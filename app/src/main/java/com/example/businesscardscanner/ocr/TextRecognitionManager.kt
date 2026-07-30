package com.example.businesscardscanner.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TextRecognitionManager(private val context: Context) {
    private companion object {
        const val TAG = "CardScannerOcr"
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun analyze(uri: Uri): OcrResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "OCR analyze(uri) input=$uri")
        val bitmap = ImagePreprocessor.loadAndEnhance(context, uri)
        Log.d(TAG, "OCR preprocess output size=${bitmap.width}x${bitmap.height}")
        analyze(bitmap)
    }

    suspend fun analyze(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "OCR analyze(bitmap) size=${bitmap.width}x${bitmap.height}")
        val image = InputImage.fromBitmap(bitmap, 0)
        val text = recognizer.process(image).await()
        val rawText = text.text.orEmpty()
        Log.d(TAG, "OCR rawText length=${rawText.length}")
        text.textBlocks.forEachIndexed { blockIndex, block ->
            val box = block.boundingBox
            Log.d(TAG, "OCR block#$blockIndex text='${block.text}' box=${box?.left ?: -1},${box?.top ?: -1},${box?.width() ?: -1},${box?.height() ?: -1}")
            block.lines.forEachIndexed { lineIndex, line ->
                val lineBox = line.boundingBox
                Log.d(TAG, "OCR block#$blockIndex line#$lineIndex text='${line.text}' box=${lineBox?.left ?: -1},${lineBox?.top ?: -1},${lineBox?.width() ?: -1},${lineBox?.height() ?: -1}")
            }
        }
        val blocks = text.textBlocks.flatMapIndexed { blockIndex, block ->
            block.lines.mapIndexed { lineIndex, line ->
                val box = line.boundingBox
                RecognizedTextBlock(
                    text = line.text.orEmpty().trim(),
                    x = box?.left ?: 0,
                    y = box?.top ?: 0,
                    width = box?.width() ?: 0,
                    height = box?.height() ?: 0,
                    lineIndex = lineIndex + blockIndex
                )
            }
        }.filter { it.text.isNotBlank() }
        if (blocks.isEmpty()) {
            Log.w(TAG, "OCR produced no text blocks")
        }
        val parsed = BusinessCardOcrParser.parse(rawText, blocks)
        Log.d(
            TAG,
            "OCR parsed name=${parsed.name.value} company=${parsed.company.value} job=${parsed.jobTitle.value} phone=${parsed.phone.value} email=${parsed.email.value} address=${parsed.address.value}"
        )
        OcrResult(
            rawText = rawText,
            name = parsed.name,
            company = parsed.company,
            jobTitle = parsed.jobTitle,
            phone = parsed.phone,
            email = parsed.email,
            website = parsed.website,
            address = parsed.address,
            social = parsed.social,
            category = parsed.category,
            blocks = blocks,
            parsedValues = parsed.values
        )
    }
}

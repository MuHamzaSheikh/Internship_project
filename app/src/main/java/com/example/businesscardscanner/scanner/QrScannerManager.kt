package com.example.businesscardscanner.scanner

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await

class QrScannerManager(private val context: Context) {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    suspend fun scan(uri: Uri): String? {
        val image = InputImage.fromFilePath(context, uri)
        val barcodes = scanner.process(image).await()
        return barcodes.firstOrNull()?.rawValue
    }
}

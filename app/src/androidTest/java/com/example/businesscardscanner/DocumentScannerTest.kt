package com.example.businesscardscanner

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.businesscardscanner.ocr.DocumentScanner
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class DocumentScannerTest {

    @Test
    fun testCropCard() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        // OpenCV needs to be initialized if it's not done in the scanner directly in a way that works for tests
        if (!org.opencv.android.OpenCVLoader.initDebug()) {
            Log.e("Test", "OpenCV init failed")
        }

        // Test with card_reference.png
        val resId = appContext.resources.getIdentifier("card_reference", "drawable", appContext.packageName)
        val bitmap = BitmapFactory.decodeResource(appContext.resources, resId)
        
        val cropped = DocumentScanner.autoCrop(bitmap, appContext.cacheDir)

        if (cropped != null) {
            Log.d("Test", "Saved cropped. Debug images should be in ${appContext.cacheDir.absolutePath}")
        } else {
            Log.d("Test", "Cropped is null")
        }
    }
}

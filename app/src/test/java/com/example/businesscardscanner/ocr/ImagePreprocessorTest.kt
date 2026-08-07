package com.example.businesscardscanner.ocr

import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePreprocessorTest {
    @Test
    fun contrastStretchExpandsDarkAndLightValues() {
        val dark = ImagePreprocessor.applyContrastStretch(32, 32, 224)
        val light = ImagePreprocessor.applyContrastStretch(224, 32, 224)

        assertTrue(dark < 32)
        assertTrue(light > 224)
    }
}

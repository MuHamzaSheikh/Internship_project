package com.example.businesscardscanner.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageAspectRatioUtilsTest {

    @Test
    fun formats_landscape_ratio_for_constraint_layout() {
        assertEquals("4:3", ImageAspectRatioUtils.toConstraintRatio(4f / 3f))
    }

    @Test
    fun formats_portrait_ratio_for_constraint_layout() {
        assertEquals("1:2", ImageAspectRatioUtils.toConstraintRatio(1f / 2f))
    }
}

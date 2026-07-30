package com.example.businesscardscanner.utils

import android.Manifest
import android.content.Context
import androidx.core.content.ContextCompat

object PermissionUtils {
    fun cameraPermission() = Manifest.permission.CAMERA

    fun galleryPermission(): String = Manifest.permission.READ_MEDIA_IMAGES

    fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, cameraPermission()) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}

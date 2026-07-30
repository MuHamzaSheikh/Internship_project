package com.example.businesscardscanner.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import com.example.businesscardscanner.R

class GalleryPermissionDialog(
    context: Context,
    private val onAllow: () -> Unit,
    private val onSkip: () -> Unit
) : Dialog(context) {
    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_gallery_permission)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            setDimAmount(0.65f)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        setCancelable(false)

        findViewById<Button>(R.id.btnAllow).setOnClickListener {
            dismiss()
            onAllow()
        }
        findViewById<Button>(R.id.btnSkip).setOnClickListener {
            dismiss()
            onSkip()
        }
    }
}

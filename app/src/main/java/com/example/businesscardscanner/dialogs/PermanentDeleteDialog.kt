package com.example.businesscardscanner.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.widget.Button
import com.example.businesscardscanner.R

class PermanentDeleteDialog(
    context: Context,
    private val onCancel: () -> Unit,
    private val onDelete: () -> Unit
) : Dialog(context) {
    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_delete_card)
        window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        setCancelable(false)

        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dismiss()
            onCancel()
        }

        findViewById<Button>(R.id.btnDelete).setOnClickListener {
            dismiss()
            onDelete()
        }
    }
}

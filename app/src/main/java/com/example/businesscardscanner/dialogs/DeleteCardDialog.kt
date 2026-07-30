package com.example.businesscardscanner.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.Window
import android.widget.Button
import com.example.businesscardscanner.R

class DeleteCardDialog(
    context: Context,
    private val onCancel: () -> Unit,
    private val onDelete: () -> Unit
) : Dialog(context) {

    private companion object {
        const val TAG = "CardScannerDelete"
    }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_delete_card)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setCancelable(false)

        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            Log.d(TAG, "Delete dialog cancel tapped")
            dismiss()
            onCancel()
        }

        findViewById<Button>(R.id.btnDelete).setOnClickListener {
            Log.d(TAG, "Delete dialog confirm tapped")
            dismiss()
            Log.d(TAG, "Delete dialog dismissed after confirm")
            onDelete()
        }
    }
}

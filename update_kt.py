import re

with open('app/src/main/java/com/example/businesscardscanner/CardEditFragment.kt', 'r') as f:
    content = f.read()

# Update bindImages
bind_images_regex = r'// Hide action buttons in QR mode to match simplified mockup.*?if \(isQrMode\)'
bind_images_replacement = '''if (isQrMode)'''
content = re.sub(bind_images_regex, bind_images_replacement, content, flags=re.DOTALL)

# Update the Card creation in btnSaveCard
save_card_regex = r'phoneSecondary = binding\.etPhoneSecondary.*?description = binding\.etDescription.*?notes = binding\.etNotes\.text\.toString\(\),'
save_card_replacement = '''description = binding.etLocation.text.toString(),'''
content = re.sub(save_card_regex, save_card_replacement, content, flags=re.DOTALL)

# Update bindFromState
bind_from_state_regex = r'bindOcrField\(binding\.etPhoneSecondary, pickField\(front\?\.phoneSecondary, back\?\.phoneSecondary\)\)\s*bindOcrField\(binding\.etEmail, pickField\(front\?\.email, back\?\.email\)\)\s*bindOcrField\(binding\.etAddress, pickField\(front\?\.address, back\?\.address\)\)\s*bindOcrField\(binding\.etDescription, pickField\(front\?\.description, back\?\.description\)\)'
bind_from_state_replacement = '''bindOcrField(binding.etEmail, pickField(front?.email, back?.email))
        bindOcrField(binding.etAddress, pickField(front?.address, back?.address))
        bindOcrField(binding.etLocation, pickField(front?.description, back?.description))'''
content = re.sub(bind_from_state_regex, bind_from_state_replacement, content, flags=re.DOTALL)

# Update bindCard
bind_card_regex = r'binding\.etPhoneSecondary\.setText\(card\.phoneSecondary\)\s*binding\.etEmail\.setText\(card\.email\)\s*binding\.etAddress\.setText\(card\.address\)\s*binding\.etDescription\.setText\(card\.description\)\s*binding\.etNotes\.setText\(card\.notes\)'
bind_card_replacement = '''binding.etEmail.setText(card.email)
        binding.etAddress.setText(card.address)
        binding.etLocation.setText(card.description)'''
content = re.sub(bind_card_regex, bind_card_replacement, content, flags=re.DOTALL)

# Update setupQuickActions
setup_quick_actions_regex = r'private fun setupQuickActions\(view: View\) \{.*?\n    \}'
setup_quick_actions_replacement = '''private fun setupQuickActions(view: View) {
        val context = requireContext()
        val etPhone = binding.etPhone
        val etLocation = binding.etLocation
        
        etPhone.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                if (event.rawX >= (etPhone.right - etPhone.compoundDrawables[2].bounds.width() - 40)) {
                    val popupBinding = com.example.businesscardscanner.databinding.LayoutPhonePopupBinding.inflate(LayoutInflater.from(context))
                    val popupWindow = android.widget.PopupWindow(
                        popupBinding.root,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        true
                    )
                    popupWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                    
                    popupBinding.actionCall.setOnClickListener {
                        val number = etPhone.text.toString().trim()
                        if (number.isNotBlank()) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:"))
                            try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No dialer found", android.widget.Toast.LENGTH_SHORT).show() }
                        }
                        popupWindow.dismiss()
                    }
                    popupBinding.actionWhatsapp.setOnClickListener {
                        val number = etPhone.text.toString().trim()
                        if (number.isNotBlank()) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://api.whatsapp.com/send?phone="))
                            try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "WhatsApp not found", android.widget.Toast.LENGTH_SHORT).show() }
                        }
                        popupWindow.dismiss()
                    }
                    popupBinding.actionMessenger.setOnClickListener {
                        val number = etPhone.text.toString().trim()
                        if (number.isNotBlank()) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("fb-messenger://user-thread/"))
                            try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "Messenger not found", android.widget.Toast.LENGTH_SHORT).show() }
                        }
                        popupWindow.dismiss()
                    }
                    
                    popupBinding.root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                    val xOffset = -popupBinding.root.measuredWidth + etPhone.width
                    popupWindow.showAsDropDown(etPhone, xOffset, 0)
                    v.performClick()
                    return@setOnTouchListener true
                }
            }
            false
        }
        
        etLocation.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                if (event.rawX >= (etLocation.right - etLocation.compoundDrawables[2].bounds.width() - 40)) {
                    val address = etLocation.text.toString().trim()
                    if (address.isNotBlank()) {
                        val encoded = android.net.Uri.encode(address)
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q="))
                        try { startActivity(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No maps app found", android.widget.Toast.LENGTH_SHORT).show() }
                    }
                    v.performClick()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }'''
content = re.sub(setup_quick_actions_regex, setup_quick_actions_replacement, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/businesscardscanner/CardEditFragment.kt', 'w') as f:
    f.write(content)
print("Updated CardEditFragment.kt")

import re

with open('app/src/main/java/com/example/businesscardscanner/CardEditFragment.kt', 'r') as f:
    content = f.read()

# Fix bindImages to remove all those unresolved references
bind_images_regex = r'// Hide action buttons in QR mode to match simplified mockup \(REVERTED: keep them visible\).*?if \(isQrMode\)'
bind_images_replacement = '''if (isQrMode)'''
content = re.sub(bind_images_regex, bind_images_replacement, content, flags=re.DOTALL)

# Wait, my previous regex didn't catch the whole block because of a mismatch.
# Let's just manually replace the block in bindImages

bind_images_regex2 = r'// Hide secondary fields and extra buttons.*?binding\.btnExportPdf\.visibility = actionVisibility\s*'
content = re.sub(bind_images_regex2, '', content, flags=re.DOTALL)

# also remove btnActionCall etc. visibility settings
action_buttons_regex = r'binding\.btnActionCall\.visibility = View\.VISIBLE\s*binding\.btnActionSms\.visibility = View\.VISIBLE\s*binding\.btnActionCallSecondary\.visibility = View\.VISIBLE\s*binding\.btnActionSmsSecondary\.visibility = View\.VISIBLE\s*binding\.btnActionEmail\.visibility = View\.VISIBLE\s*binding\.btnActionMap\.visibility = View\.VISIBLE'
content = re.sub(action_buttons_regex, '', content, flags=re.DOTALL)

# And remove tvPhoneSecondaryLabel, llPhoneSecondary, tvDescriptionLabel, etDescription, tvNotesLabel, etNotes, btnSaveToContacts, btnExportPdf
unresolved_regex = r'binding\.tvPhoneSecondaryLabel\.visibility = actionVisibility\s*binding\.llPhoneSecondary\.visibility = actionVisibility\s*binding\.tvDescriptionLabel\.visibility = actionVisibility\s*binding\.etDescription\.visibility = actionVisibility\s*binding\.tvNotesLabel\.visibility = actionVisibility\s*binding\.etNotes\.visibility = actionVisibility\s*binding\.btnSaveToContacts\.visibility = actionVisibility\s*binding\.btnExportPdf\.visibility = actionVisibility'
content = re.sub(unresolved_regex, '', content, flags=re.DOTALL)

with open('app/src/main/java/com/example/businesscardscanner/CardEditFragment.kt', 'w') as f:
    f.write(content)
print("Fixed CardEditFragment.kt")

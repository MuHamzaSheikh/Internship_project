import re

with open('app/src/main/res/layout/fragment_card_edit.xml', 'r') as f:
    content = f.read()

# Remove btnActionCall, btnActionSms and replace etPhone with drawableEnd
phone_replacement = '''            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginTop="12dp" android:text="Phone" android:textColor="@color/TextPrimary" android:textSize="13sp" />
            <EditText
                android:id="@+id/etPhone"
                android:layout_width="match_parent"
                android:layout_height="44dp"
                android:layout_marginTop="6dp"
                android:background="@drawable/edit_text_outline"
                android:hint="0123456789"
                android:inputType="phone"
                android:paddingStart="12dp"
                android:paddingEnd="12dp"
                android:drawableEnd="@drawable/ic_dropdown_arrow"
                android:drawablePadding="8dp"
                android:textColor="@color/TextPrimary"
                android:textColorHint="@color/TextSecondary" />'''

# The regex replaces Phone 1 block
content = re.sub(
    r'<TextView[^>]+text=\"Phone 1\"[^>]*>\s*<LinearLayout.*?android:id=\"@\+id/btnActionSms\"[^>]*>\s*</LinearLayout>',
    phone_replacement,
    content,
    flags=re.DOTALL
)

# Remove Phone 2 entirely
content = re.sub(
    r'<TextView android:id=\"@\+id/tvPhoneSecondaryLabel\".*?</LinearLayout>',
    '',
    content,
    flags=re.DOTALL
)

# Replace Email to remove btnActionEmail
email_replacement = '''            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginTop="12dp" android:text="Email" android:textColor="@color/TextPrimary" android:textSize="13sp" />
            <EditText
                android:id="@+id/etEmail"
                android:layout_width="match_parent"
                android:layout_height="44dp"
                android:layout_marginTop="6dp"
                android:background="@drawable/edit_text_outline"
                android:hint="Michal Smith"
                android:inputType="textEmailAddress"
                android:paddingStart="12dp"
                android:paddingEnd="12dp"
                android:textColor="@color/TextPrimary"
                android:textColorHint="@color/TextSecondary" />'''
content = re.sub(
    r'<TextView[^>]+text=\"Email\"[^>]*>\s*<LinearLayout.*?android:id=\"@\+id/btnActionEmail\"[^>]*>\s*</LinearLayout>',
    email_replacement,
    content,
    flags=re.DOTALL
)

# Replace Address to remove btnActionMap
address_replacement = '''            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginTop="12dp" android:text="Address" android:textColor="@color/TextPrimary" android:textSize="13sp" />
            <EditText
                android:id="@+id/etAddress"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:background="@drawable/edit_text_outline"
                android:hint="Building # 123, 1st Floor Apartment no 101"
                android:inputType="textMultiLine|textNoSuggestions"
                android:minHeight="44dp"
                android:minLines="2"
                android:paddingStart="12dp"
                android:paddingTop="12dp"
                android:paddingEnd="12dp"
                android:paddingBottom="12dp"
                android:textColor="@color/TextPrimary"
                android:textColorHint="@color/TextSecondary" />'''
content = re.sub(
    r'<TextView[^>]+text=\"Address\"[^>]*>\s*<LinearLayout.*?android:id=\"@\+id/btnActionMap\"[^>]*>\s*</LinearLayout>',
    address_replacement,
    content,
    flags=re.DOTALL
)

# Replace Description with Location and remove Notes
location_replacement = '''            <TextView android:id="@+id/tvLocationLabel" android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginTop="12dp" android:text="Location" android:textColor="@color/TextPrimary" android:textSize="13sp" />
            <EditText
                android:id="@+id/etLocation"
                android:layout_width="match_parent"
                android:layout_height="44dp"
                android:layout_marginTop="6dp"
                android:background="@drawable/edit_text_outline"
                android:hint="Street,123 New City"
                android:inputType="text"
                android:paddingStart="12dp"
                android:paddingEnd="12dp"
                android:drawableEnd="@drawable/ic_map"
                android:drawablePadding="8dp"
                android:textColor="@color/TextPrimary"
                android:textColorHint="@color/TextSecondary" />'''
content = re.sub(
    r'<TextView android:id=\"@\+id/tvDescriptionLabel\".*?<EditText android:id=\"@\+id/etNotes\"[^>]*>',
    location_replacement,
    content,
    flags=re.DOTALL
)

# Update Save Card button style
save_btn_replacement = '''        <Button
            android:id="@+id/btnSaveCard"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="0dp"
            android:layout_height="48dp"
            android:layout_marginStart="16dp"
            android:layout_marginTop="24dp"
            android:layout_marginEnd="16dp"
            android:text="Save Card"
            android:textAllCaps="false"
            android:textColor="@color/TextPrimary"
            app:strokeColor="@color/PrimaryBlue"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/formBlock" />'''

# Remove btnSaveToContacts and btnExportPdf, update btnSaveCard
content = re.sub(
    r'<Button\s*android:id=\"@\+id/btnSaveToContacts\".*?android:id=\"@\+id/btnSaveCard\"[^>]*>',
    save_btn_replacement,
    content,
    flags=re.DOTALL
)

with open('app/src/main/res/layout/fragment_card_edit.xml', 'w') as f:
    f.write(content)
print("Updated fragment_card_edit.xml")

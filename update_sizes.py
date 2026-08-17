import xml.etree.ElementTree as ET
import re

def update_text_sizes(file_path, tag_names):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # We will use a regex to safely add android:textSize="12sp" to specified tags if not present
    for tag in tag_names:
        # Match <Tag ... /> or <Tag ... >
        pattern = re.compile(r'(<'+tag+r'\b[^>]*?)(/?>)', re.DOTALL)
        
        def replacer(match):
            tag_content = match.group(1)
            end_bracket = match.group(2)
            
            # If already has textSize, don't add
            if 'android:textSize' in tag_content:
                return match.group(0)
                
            # If it's a TextView, ensure it has an id starting with @+id/txt
            if tag == 'TextView' and 'android:id="@+id/txt' not in tag_content:
                return match.group(0)
                
            return tag_content + ' android:textSize="12sp"' + end_bracket
            
        content = pattern.sub(replacer, content)
        
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)

update_text_sizes('app/src/main/res/layout/fragment_card_edit.xml', ['EditText', 'com.google.android.material.textfield.MaterialAutoCompleteTextView'])
update_text_sizes('app/src/main/res/layout/fragment_after_qr_scan.xml', ['EditText', 'com.google.android.material.textfield.MaterialAutoCompleteTextView'])
update_text_sizes('app/src/main/res/layout/fragment_card_preview.xml', ['TextView'])

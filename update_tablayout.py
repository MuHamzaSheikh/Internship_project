import re

def update_tab_layout(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # Find the TabLayout block and add app:tabMinWidth="16dp"
    # and maybe change tabPadding to 0dp if it's there
    regex = r'(<com\.google\.android\.material\.tabs\.TabLayout[^>]+id=\"@\+id/pageDots\"[^>]*>)'
    
    def replacer(match):
        tab_tag = match.group(1)
        if 'app:tabMinWidth' not in tab_tag:
            tab_tag = tab_tag.replace('app:tabGravity="center"', 'app:tabGravity="center"\n            app:tabMinWidth="16dp"')
        return tab_tag
        
    new_content = re.sub(regex, replacer, content, flags=re.DOTALL)
    
    with open(filepath, 'w') as f:
        f.write(new_content)

update_tab_layout('app/src/main/res/layout/fragment_card_preview.xml')
update_tab_layout('app/src/main/res/layout/fragment_card_edit.xml')
print("Updated layouts")

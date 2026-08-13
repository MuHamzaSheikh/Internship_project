import re

with open('app/src/main/java/com/example/businesscardscanner/CardEditFragment.kt', 'r') as f:
    content = f.read()

# Make absolutely sure actionVisibility and the remaining buttons are gone.
regex = r'val actionVisibility = if \(isQrMode\) View\.GONE else View\.VISIBLE\s*'
content = re.sub(regex, '', content, flags=re.DOTALL)

with open('app/src/main/java/com/example/businesscardscanner/CardEditFragment.kt', 'w') as f:
    f.write(content)
print("Verified CardEditFragment.kt")

$layoutsPath = 'app/src/main/res/layout'

Get-ChildItem -Path $layoutsPath -Filter *.xml -Recurse | ForEach-Object {
    $filePath = $_.FullName
    $content = Get-Content $filePath -Raw

    # Remove any existing android:textSize="12sp" to avoid duplicates
    $content = $content -replace 'android:textSize="12sp"\s*', ''

    # Add android:textSize="12sp" to EditText
    $content = [regex]::Replace($content, '(<EditText[^>]*?)(/?>)', '$1 android:textSize="12sp"$2', [System.Text.RegularExpressions.RegexOptions]::Singleline)

    # Add android:textSize="12sp" to MaterialAutoCompleteTextView
    $content = [regex]::Replace($content, '(<com\.google\.android\.material\.textfield\.MaterialAutoCompleteTextView[^>]*?)(/?>)', '$1 android:textSize="12sp"$2', [System.Text.RegularExpressions.RegexOptions]::Singleline)

    [IO.File]::WriteAllText($filePath, $content)
}

$previewPath = 'app/src/main/res/layout/fragment_card_preview.xml'
$content = Get-Content $previewPath -Raw
$content = [regex]::Replace($content, '(<TextView\s+android:id="@+id/txt[^>]*?)(/?>)', '$1 android:textSize="12sp"$2', [System.Text.RegularExpressions.RegexOptions]::Singleline)
[IO.File]::WriteAllText($previewPath, $content)

Write-Host "Done"

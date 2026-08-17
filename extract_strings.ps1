$layoutsPath = 'app/src/main/res/layout'
$stringsMap = @{}
$counter = 1

function Get-StringKey ($value) {
    $key = $value -replace '[^a-zA-Z0-9]', '_' -replace '_+', '_' -replace '^_|_$', ''
    $key = $key.ToLower()
    if ($key.Length -gt 30) {
        $key = $key.Substring(0, 30) -replace '_$', ''
    }
    if ([string]::IsNullOrWhiteSpace($key)) {
        $key = "str_key_$counter"
        $script:counter++
    } else {
        $key = "str_$key"
    }
    return $key
}

Get-ChildItem -Path $layoutsPath -Filter *.xml -Recurse | ForEach-Object {
    $filePath = $_.FullName
    $content = Get-Content $filePath -Raw

    $matches = [regex]::Matches($content, 'android:text="([^@][^"]*)"')
    foreach ($match in $matches) {
        $val = $match.Groups[1].Value
        $key = Get-StringKey $val
        if (-not $stringsMap.ContainsKey($key)) {
            $stringsMap[$key] = $val
        }
        $replacement = 'android:text="@string/' + $key + '"'
        $content = $content.Replace($match.Value, $replacement)
    }

    $matches = [regex]::Matches($content, 'android:hint="([^@][^"]*)"')
    foreach ($match in $matches) {
        $val = $match.Groups[1].Value
        $key = Get-StringKey $val
        if (-not $stringsMap.ContainsKey($key)) {
            $stringsMap[$key] = $val
        }
        $replacement = 'android:hint="@string/' + $key + '"'
        $content = $content.Replace($match.Value, $replacement)
    }

    [IO.File]::WriteAllText($filePath, $content)
}

$res = "<resources>
"
$res += '    <string name="app_name">Business Card Scanner</string>' + "
"
$res += '    <string name="back_capture_prompt_title">Capture back side?</string>' + "
"
$res += '    <string name="back_capture_prompt_message">Would you like to capture the back side of this card?</string>' + "
"
$res += '    <string name="capture_back">Capture Back</string>' + "
"
$res += '    <string name="skip_back_capture">Skip</string>' + "
"

foreach ($key in $stringsMap.Keys | Sort-Object) {
    $val = $stringsMap[$key]
    $escapedVal = $val -replace "'", "\'" -replace '"', '\"' -replace '&', '&amp;'
    $escapedVal = $escapedVal -replace '&amp;amp;', '&amp;'
    $res += '    <string name="' + $key + '">' + $escapedVal + '</string>' + "
"
}
$res += "</resources>
"

[IO.File]::WriteAllText('app/src/main/res/values/strings.xml', $res)
Write-Host "Done extracting strings"

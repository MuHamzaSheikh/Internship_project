adb logcat -c
./gradlew installDebug
adb shell am start -n com.example.businesscardscanner/.CardWorkflowActivity --es extra_start_step CARD_EDIT
Start-Sleep -Seconds 3
adb shell input swipe 500 1500 500 500
Start-Sleep -Seconds 1
adb shell input swipe 500 1500 500 500
Start-Sleep -Seconds 2
adb logcat -d | Select-String -Pattern "fatal|crash|exception|error"

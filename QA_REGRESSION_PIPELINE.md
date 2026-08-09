# QA Regression Pipeline Checklist

**CRITICAL WARNING:** This pipeline (auto-crop, corner detection, auto-enhancement, dullness detection) has a history of silent regressions. 
If you modify ANY of the following files, you MUST run this manual QA checklist or execute the `scripts/verify_pipeline.kts` script to confirm nothing has broken before committing your changes.

**Monitored Files:**
- `DocumentScanner.kt`
- `ImagePreprocessor.kt`
- `CaptureDoneFragment.kt`
- `CaptureFragment.kt`

## Checklist

### 1. Test Auto-Crop (Corner Detection)
- [ ] Load a sample image of a business card captured at a perspective angle (e.g., `card_reference.png`).
- [ ] Observe the `Logcat` output for the tag `CardScannerDocScan`. You MUST see the message `"PIPELINE INVOCATION: detectCorners started"`.
- [ ] Verify that `DocumentScanner.detectCorners()` successfully returns an array of 4 points.
- [ ] Verify that `cropByCorners()` produces a straightened, cropped image of only the business card.

### 2. Test Auto-Enhancement (Dullness & Brightness)
- [ ] Ensure that the UI defaults to the **"Auto (Recommended)"** chip, and not "Original".
- [ ] Observe the `Logcat` output for the tag `CardScannerEnhance`. You MUST see the message `"PIPELINE INVOCATION: applyAdaptiveEnhancement started"`.
- [ ] Load a "dull" reference image (low contrast). 
- [ ] Verify that the `Contrast (StdDev)` log outputs a value less than 40.0.
- [ ] Verify that the resulting enhanced image is visibly punchier with expanded contrast (due to the CLAHE and histogram stretch).

### 3. Save & Review
- [ ] Verify the final image saved to disk matches the cropped, auto-enhanced preview, and that the aspect ratio matches the physical card.

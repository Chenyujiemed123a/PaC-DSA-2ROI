# Validation scope

## Public automated suite

`tests/ReleaseTests.java` contains only formulas, hand-constructed artificial test signals and generated pixels. It does not include anonymized patient traces. The actual local environment and pass output are recorded in `validation/build-and-test.json`.

Ten test groups cover:

1. Two-ROI selection, manager ordering, rejection of ambiguous selections and non-mutation of selection indices.
2. Area ROI geometry checks and rejection of missing, line, point or out-of-bounds ROIs.
3. Positive/negative synthetic signal polarity, TTP 17/19 and known +2-frame difference.
4. Preservation of the input numerical trace.
5. Rejection of a flat signal.
6. Exclusion of a later, larger synthetic peak by the first-pass analysis window.
7. Acceptance of a clean local parabolic peak and rejection of a plateau.
8. Correct known peak in manual-window mode.
9. A locally accepted interior fit still retaining a search-end-maximum warning; `Peak_selection_stability` remains `Not tested` in both result rows.
10. Generation, save/reopen and analysis of a 69-frame 16-bit synthetic TIFF plus an actual saved ROI ZIP, yielding the independently specified TTP 17/19 and +2-frame difference.

The synthetic strip uses a Gaussian pulse of amplitude 800 above baseline 2000, width parameter 3 frames, and a linearly varying delay along its length. The prescribed ROI-center difference corresponds to two frames. See source for exact coordinates and generation formula. This is a software fixture, not a model validated against cerebral circulation.

## What has not been demonstrated

- Clinical accuracy, reproducibility across operators, or agreement with an external ground truth.
- Accuracy for arbitrary contrast-injection profiles, noisy/moving images, stenoses, overlapping vessels or variable frame rates.
- Accuracy of fractional-frame parabolic estimates.
- End-to-end automated GUI tests on all supported operating systems.
- GitHub Actions results before the repository is actually created and CI runs.

Private historical cases are intentionally not distributed or used as public fixtures. No patient's measurements are silently substituted with synthetic values. Original local case files remain outside this release directory.

## Reproduction

Run `python3 scripts/build.py` (or `python` on Windows). Inspect the JSON report and verify `SHA256SUMS.txt`. The build aborts on a dependency hash mismatch, unexpected algorithm-source hash, compilation failure or test assertion failure.

The package contains no ImageJ dependency JAR, JDK, patient archive or secret. Build outputs are generated from the included source. Byte-identical JAR builds are expected when compiler and tooling are held fixed; different JDK releases may emit different class bytes even for identical source.

# Installation and five-step walkthrough

## 1. Installation

Use an existing Fiji installation with ImageJ1. The local build/reference dependency is ImageJ 1.54p; the tested Java runtime is Zulu 21.0.7 on macOS arm64. Other environments must be tested before claiming support.

Copy `dist/PaC_DSA_2ROI.jar` into the installation's `plugins` directory. Back up any previous JAR **outside** that directory. Avoid duplicate class files or JARs providing `PaC_DSA_2ROI`. Restart Fiji. The command should appear as **Plugins → PaC DSA 2ROI** and its dialog should show `2026.10.07-quality.1` where the version is displayed.

Official ImageJ1 plugin installation/registration reference: https://imagej.net/develop/ij1-plugins

## 2. Image preparation

Open the original time series as a grayscale stack: at least two frames, one plane per acquisition time. This plugin processes stack index, not arbitrary multidimensional channel/z/time axes. Do not supply RGB screenshots, rendered plots, or an interleaved multi-channel/z stack as if it were a temporal series.

Verify frame ordering, acquisition timing and intended anatomical projection yourself. The software checks neither vessel identity nor DICOM sequence correctness. Do not apply destructive edits to the only copy of a source series.

For the public example, use `examples/synthetic/synthetic_6fps.tif`: 128×128 pixels, 16 bit, 69 frames, 6 frames/s. It is an artificial strip, not a DSA anatomy demonstration.

## 3. ROI placement and reuse

Open **Analyze → Tools → ROI Manager**. Create an area ROI and press **T** to add it. Add proximal/reference first and distal/comparison second. Both must be entirely inside the image. The plugin measures fixed coordinates across all frames; it does not track movement.

For the example, use an empty manager and **More → Open** to load `synthetic_ROIs.zip`. If the manager contains more than two entries, select exactly the intended pair. Roles follow manager order, not selection-click order.

To preserve editable coordinates, select the intended pair and use **More → Save**, specifying a `.zip` file. Reopen it later only against the correct image geometry. A position screenshot is useful for visual audit but does not replace this coordinate archive.

## 4. TTP extraction

Activate the time-series image and launch **Plugins → PaC DSA 2ROI**.

| Parameter | Synthetic example |
| --- | --- |
| Automatically detect first upslope | On |
| Local parabolic refinement | On |
| Smoothing radius | 1 (three-point mean) |
| Sustained-rise span | 3 frame intervals |
| Noise multiplier | 3 |
| First main-peak threshold | 90% |
| Maximum first-pass duration | 3 s |
| Frame rate | 6 frames/s |

Manual start/end apply only when automatic detection is off. Use the actual acquisition frame rate for real data, not the example's rate. `0` means unknown and suppresses seconds reporting; it also changes the window-length heuristic.

Expected synthetic result: TTP 17/19, signed difference +2 frames, 0.3333 s. Review both the plot and Log for warnings. Never convert a negative difference to an absolute value without resolving ROI order and timing.

## 5. Output and quality control

Save the Results table, Log text, parameter settings, curve plot and ROI position image. Retain the ROI ZIP and source-series identifier privately. The plugin does not implement one-click comprehensive archival; some parameters appear only in the settings dialog, so record them explicitly.

For a position image, ensure both ROIs are visible in **Show All**, use **Image → Overlay → Flatten** on the desired frame, then save the new rendered image as PNG or TIFF. Verify that both boxes and the whole intended view are present. Keep the original time stack unchanged. Saving a whole stack can yield a multipage TIFF; it is not the same as a single flattened position image.

For each run, record algorithm version, ROI roles, source stack/sequence, frame rate and timing basis, all settings, both integer TTP values, signed delta, local-fit status and candidates, warnings, and independent repeat/reviewer results if performed. Do not replace an integer result with a local-fit candidate that was rejected.

## Troubleshooting

- Missing menu command: check the plugins folder, restart, and remove duplicate older copies after backing them up.
- ROI errors: use area ROIs fully within the source image; with more than two manager entries select exactly two.
- Wrong image or no stack: activate the original time sequence, not the curve/results image.
- Flat or unrecognized curve: verify the actual ROI, temporal stack and signal; do not force a number.
- Saving dialog stalls: retain the open source and manager; resolve the modal dialog before invoking more actions. Do not terminate Fiji without saving important unsaved ROIs/results.

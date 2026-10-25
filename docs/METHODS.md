# Algorithm and limitations

This description is derived from the included `PaC_DSA_2ROI.java`, algorithm version `2026.10.07-quality.1`. It describes implementation behavior, not clinical validation or a claim of methodological novelty.

## Signal and timing

Each area ROI's mean calibrated pixel intensity is measured at identical coordinates in every stack plane. The plugin duplicates each frame's processor for measurement and does not deliberately invert or alter source pixels. It does not perform image registration or ROI tracking.

A centered moving mean uses radius `r` (nominal width `2r+1`), shortened at sequence ends. Smoothing can shift a selected discrete peak; it is not guaranteed to improve timing accuracy for every curve.

In automatic mode, both signal polarities are searched. A robust leading-difference noise estimate, sustained-rise condition and a later confirmation increase are used to select an early candidate. The leading region uses up to `max(8, round(0.25N))` differences, capped by `N−1`. Its MAD is scaled by 1.4826; a small fraction of the robust signal range is used as a fallback. Thus this is a heuristic leading-noise estimate, not a known pre-contrast-only noise sample. Early contrast arrival or motion can contaminate it.

With default span 3 and noise multiplier 3, a candidate requires predominantly positive steps in the direction-adjusted signal, no excessive counter-step, a net rise exceeding both noise- and signal-range-based thresholds, and a further rise in a confirmation horizon. The implementation does **not** guarantee that the earliest detected rise is the true first physiological contrast passage.

Baseline is the median of smoothed samples before the selected onset (or the first few samples at the beginning). The baseline-corrected signal is `direction × (smoothed signal − baseline)`. This subtracts a scalar; it is not time-varying background-ROI correction.

The first-pass window starts at the detected onset and ends at the configured duration converted to frame intervals, capped at the sequence end. With unknown frame rate, a frame-count heuristic replaces the seconds-based limit. In manual mode, explicit start/end frames and a window-based polarity rule are used.

Within that window, the selected integer peak is the **first local maximum** meeting the configured fraction of the window maximum (default 90%). If no local maximum qualifies, the implementation falls back to the window maximum. Consequently the selected point need not be the window's absolute maximum. Later recirculation is excluded only to the extent the onset/window and first-main-peak rules actually separate it.

Frame numbering is one-based. Reported single-ROI time is `(TTP_frame−1)/fps`; the signed difference is `(TTP2_frame−TTP1_frame)/fps`. These are sequence-referenced times, not automatically arrival-to-peak durations. The plugin reads a uniform frame interval or an FPS marker in the title; it does not implement a DICOM variable-frame-time-vector analysis. Verify timing externally.

## Local parabolic refinement

An optional three-point parabola uses the selected discrete peak and its immediate neighbors. A candidate must meet local checks including a strict interior maximum, downward curvature, adequate curvature relative to noise and a vertex offset no larger than half a frame. Acceptance additionally excludes fallback/multiple-qualifying-peak cases and insufficient peak-to-noise amplitude.

The Results table may contain a **computed but rejected** local candidate. Consult `Local_fit_status` and `Local_fit_reason`; presence of a number alone is not acceptance. A fitted delta is reported in Log only when both local fits pass. Interpolation produces a fractional-frame estimate, not new temporal samples or proven subframe accuracy.

## Quality statuses are deliberately separate

- **Local fit quality:** whether the local three-point fit met implementation checks.
- **Window status:** warns if the maximum remains at the search end, even when an earlier internal local peak was selected. Absence of this warning does not prove window completeness.
- **Peak selection stability:** always `Not tested`. The plugin does not perturb ROIs or independently re-place them to validate spatial repeatability.

Other warnings concern multiple peaks, fallback selection, selected boundary peaks, late onset, weak signal, widely separated onsets and opposite detected polarities. Warnings must not be silently dropped from an exported summary.

## Scope limits

Repeated annotation and independent observers are external validation procedures, not automatic plugin outputs. Agreement within a project's chosen frame threshold demonstrates that particular comparison, not ground-truth accuracy or immunity to shared bias.

The plugin cannot establish whether ROIs lie in the intended artery, whether overlapping vessels contaminate a signal, or whether motion, stenosis, bolus dispersion, injection differences or recirculation invalidate a transport interpretation. It reports timing of an intensity feature. It does not directly calculate volumetric flow or validate an area/length/timing conversion. No clinical decision should rely on this candidate alone.

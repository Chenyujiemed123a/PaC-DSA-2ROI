import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares the time-to-peak (TTP) of two area ROIs in ROI Manager.
 * If more than two entries exist, exactly two must be explicitly selected.
 * Their order in the Manager list defines reference and comparison ROIs.
 *
 * In automatic mode, the plugin estimates leading noise from frame-to-frame
 * differences with a robust MAD estimator, detects the earliest sustained
 * upslope in either signal direction, and then finds the first major local
 * peak within a limited first-pass duration. Manual first-pass limits remain
 * available as a fallback for curves with severe motion or subtraction error.
 */
public class PaC_DSA_2ROI implements PlugIn {

    private static final String VERSION = "2026.10.07-quality.1";
    private static final String TITLE = "PaC DSA 2ROI " + VERSION;
    private static final String LOCAL_FIT_ACCEPTED =
        "Local parabolic fit accepted; peak selection stability not tested";
    private static final String PEAK_STABILITY_NOT_TESTED = "Not tested";
    private static final String WINDOW_END_WARNING =
        "Window completeness unconfirmed: search-window maximum is at the end boundary";

    private static final Pattern FPS_IN_TITLE = Pattern.compile(
        "(?i)([0-9]+(?:\\.[0-9]+)?)\\s*_?\\s*FPS"
    );
    private static final double MAD_TO_SIGMA = 1.4826;
    private static final double RANGE_RISE_FRACTION = 0.10;
    private static final double RANGE_CONFIRM_FRACTION = 0.25;

    @Override
    public void run(String arg) {
        ImagePlus image = IJ.getImage();
        if (image == null || image.getStackSize() < 2) {
            IJ.showMessage("PaC DSA 2ROI", "Please open a DSA image stack first.");
            return;
        }

        RoiManager manager = RoiManager.getInstance();
        if (manager == null || manager.getCount() < 2) {
            IJ.showMessage(
                "PaC DSA 2ROI",
                "Please add two vessel ROIs to ROI Manager.\n" +
                "ROI 1 is the reference vessel; ROI 2 is the comparison vessel."
            );
            return;
        }

        int[] roiIndices;
        try {
            roiIndices = selectRoiIndices(manager.getCount(), manager.getSelectedIndexes());
        } catch (IllegalArgumentException error) {
            IJ.showMessage(TITLE, error.getMessage());
            return;
        }
        Roi roi1 = manager.getRoi(roiIndices[0]);
        Roi roi2 = manager.getRoi(roiIndices[1]);
        String error1 = roiValidationError(roi1, image.getWidth(), image.getHeight());
        String error2 = roiValidationError(roi2, image.getWidth(), image.getHeight());
        if (error1 != null || error2 != null) {
            String message = error1 == null ? "" : "ROI 1 (Manager entry " +
                (roiIndices[0] + 1) + "): " + error1;
            if (error2 != null) {
                if (!message.isEmpty()) message += "\n";
                message += "ROI 2 (Manager entry " + (roiIndices[1] + 1) + "): " + error2;
            }
            IJ.showMessage(TITLE, message);
            return;
        }
        roi1 = (Roi) roi1.clone();
        roi2 = (Roi) roi2.clone();
        String name1 = roiName(manager, roiIndices[0], "ROI 1");
        String name2 = roiName(manager, roiIndices[1], "ROI 2");

        int frameCount = image.getStackSize();
        AnalysisSettings settings = askForSettings(image, frameCount);
        if (settings == null) {
            return;
        }

        double[] curve1 = measureMeanCurve(image, roi1);
        double[] curve2 = measureMeanCurve(image, roi2);
        CurveAnalysis analysis1 = analyzeCurve(curve1, settings);
        CurveAnalysis analysis2 = analyzeCurve(curve2, settings);

        if (analysis1 == null || analysis2 == null) {
            IJ.showMessage(
                "PaC DSA 2ROI",
                "Automatic first-upslope detection was not reliable for at least one ROI.\n" +
                "Run the plugin again, uncheck automatic detection, and enter a\n" +
                "manual first-pass window that contains the true initial upslope."
            );
            return;
        }

        int ttp1 = analysis1.peak.index + 1;
        int ttp2 = analysis2.peak.index + 1;
        int deltaFrames = ttp2 - ttp1;
        ParabolicFit fit1 = settings.parabolicRefinement
            ? interpolatePeak(analysis1)
            : ParabolicFit.notComputed("disabled in settings");
        ParabolicFit fit2 = settings.parabolicRefinement
            ? interpolatePeak(analysis2)
            : ParabolicFit.notComputed("disabled in settings");
        IJ.log("Selected Manager entries: " + (roiIndices[0] + 1) + " / " +
            (roiIndices[1] + 1) + " (ROI 1 proximal/reference; ROI 2 distal/comparison)");
        logResults(
            name1,
            name2,
            ttp1,
            ttp2,
            deltaFrames,
            settings,
            analysis1,
            analysis2,
            fit1,
            fit2,
            frameCount
        );
        createResultsTable(name1, name2, analysis1, analysis2, fit1, fit2)
            .show("PaC DSA 2ROI Results " + VERSION);
        showComparisonPlot(
            analysis1,
            analysis2,
            name1,
            name2,
            ttp1,
            ttp2,
            fit1,
            fit2,
            settings
        );
    }

    static int[] selectRoiIndices(int count, int[] selectedIndices) {
        if (count < 2) {
            throw new IllegalArgumentException("Please add two vessel area ROIs to ROI Manager.");
        }
        if (count == 2) return new int[] {0, 1};

        int selectedCount = selectedIndices == null ? 0 : selectedIndices.length;
        if (selectedCount != 2) {
            throw new IllegalArgumentException(
                "ROI Manager contains " + count + " entries. Select exactly two ROIs for this image.\n" +
                "Currently selected: " + selectedCount + ".\n" +
                "The earlier entry in the Manager list is ROI 1 (proximal/reference);\n" +
                "the later entry is ROI 2 (distal/comparison)."
            );
        }
        int[] indices = selectedIndices.clone();
        Arrays.sort(indices);
        if (indices[0] < 0 || indices[1] >= count || indices[0] == indices[1]) {
            throw new IllegalArgumentException("The selected ROI entries are invalid. Select two distinct entries again.");
        }
        return indices;
    }

    static String roiValidationError(Roi roi, int imageWidth, int imageHeight) {
        if (roi == null) return "The ROI is missing. Please add it again.";
        if (!roi.isArea()) {
            return "Use an area ROI, such as a rectangle, oval, or polygon; lines and points cannot be analyzed.";
        }
        Rectangle2D bounds = roi.getFloatBounds();
        if (bounds == null || !Double.isFinite(bounds.getX()) ||
                !Double.isFinite(bounds.getY()) || !Double.isFinite(bounds.getWidth()) ||
                !Double.isFinite(bounds.getHeight()) || bounds.getWidth() <= 0.0 ||
                bounds.getHeight() <= 0.0) {
            return "The ROI has no valid area. Please redraw it.";
        }
        if (imageWidth <= 0 || imageHeight <= 0 || bounds.getX() < 0.0 ||
                bounds.getY() < 0.0 || bounds.getMaxX() > imageWidth ||
                bounds.getMaxY() > imageHeight) {
            return "The entire ROI must lie inside the current image (" +
                imageWidth + " x " + imageHeight + " pixels).";
        }
        return null;
    }

    private AnalysisSettings askForSettings(ImagePlus image, int frameCount) {
        int defaultEnd = Math.min(
            frameCount,
            Math.max(10, (int) Math.round(frameCount * 0.60))
        );
        double defaultFps = detectFrameRate(image);

        GenericDialog dialog = new GenericDialog(TITLE + " - Automatic TTP settings");
        dialog.addMessage(
            "Automatic mode estimates leading noise, finds the earliest sustained\n" +
            "first upslope, and ignores later recirculation peaks. The detected\n" +
            "upslope starts and TTP points are marked on the output plot.\n" +
            "Optional parabolic refinement reports sub-frame TTP only when\n" +
            "local peak-shape and signal-quality checks pass.\n" +
            "Peak selection stability across ROI changes is not tested."
        );
        dialog.addCheckbox("Automatically detect first upslope", true);
        dialog.addCheckbox("Use parabolic sub-frame peak refinement", true);
        dialog.addNumericField("Manual start frame (only if auto is off)", 1, 0);
        dialog.addNumericField("Manual end frame (only if auto is off)", defaultEnd, 0);
        dialog.addNumericField("Smoothing radius (1 = 3-point)", 1, 0);
        dialog.addNumericField("Sustained-rise span (frame intervals)", 3, 0);
        dialog.addNumericField("Noise multiplier", 3.0, 1);
        dialog.addNumericField("First main-peak threshold (%)", 90.0, 1);
        dialog.addNumericField("Maximum first-pass duration (seconds)", 3.0, 1);
        dialog.addNumericField("Frame rate (frames/s; 0 = unknown)", defaultFps, 3);
        dialog.showDialog();
        if (dialog.wasCanceled()) {
            return null;
        }

        boolean automatic = dialog.getNextBoolean();
        boolean parabolicRefinement = dialog.getNextBoolean();
        int manualStart = (int) Math.round(dialog.getNextNumber());
        int manualEnd = (int) Math.round(dialog.getNextNumber());
        int smoothingRadius = (int) Math.round(dialog.getNextNumber());
        int riseSpan = (int) Math.round(dialog.getNextNumber());
        double noiseMultiplier = dialog.getNextNumber();
        double peakThresholdPercent = dialog.getNextNumber();
        double maximumFirstPassSeconds = dialog.getNextNumber();
        double frameRate = dialog.getNextNumber();

        if (!automatic && (
            manualStart < 1 ||
            manualEnd > frameCount ||
            manualStart >= manualEnd
        )) {
            IJ.showMessage(
                "PaC DSA 2ROI",
                "The manual window must satisfy:\n" +
                "1 <= start frame < end frame <= " + frameCount + "."
            );
            return null;
        }
        if (smoothingRadius < 0 || smoothingRadius > 10) {
            IJ.showMessage(
                "PaC DSA 2ROI",
                "The smoothing radius must be between 0 and 10 frames."
            );
            return null;
        }
        if (riseSpan < 2 || riseSpan > 8) {
            IJ.showMessage(
                "PaC DSA 2ROI",
                "The sustained-rise span must be between 2 and 8 frame intervals."
            );
            return null;
        }
        if (noiseMultiplier < 1.0 || noiseMultiplier > 10.0) {
            IJ.showMessage(
                "PaC DSA 2ROI",
                "The noise multiplier must be between 1.0 and 10.0."
            );
            return null;
        }
        if (peakThresholdPercent < 50.0 || peakThresholdPercent > 100.0) {
            IJ.showMessage(
                "PaC DSA 2ROI",
                "The main-peak threshold must be between 50% and 100%."
            );
            return null;
        }
        if (maximumFirstPassSeconds <= 0.0 || Double.isNaN(maximumFirstPassSeconds)) {
            IJ.showMessage(
                "PaC DSA 2ROI",
                "Maximum first-pass duration must be greater than zero."
            );
            return null;
        }
        if (frameRate < 0.0 || Double.isNaN(frameRate)) {
            IJ.showMessage("PaC DSA 2ROI", "Frame rate cannot be negative.");
            return null;
        }

        return new AnalysisSettings(
            automatic,
            parabolicRefinement,
            manualStart,
            manualEnd,
            smoothingRadius,
            riseSpan,
            noiseMultiplier,
            peakThresholdPercent / 100.0,
            maximumFirstPassSeconds,
            frameRate
        );
    }

    private double[] measureMeanCurve(ImagePlus image, Roi roi) {
        int frameCount = image.getStackSize();
        double[] values = new double[frameCount];
        Calibration calibration = image.getCalibration();

        for (int frame = 1; frame <= frameCount; frame++) {
            ImageProcessor processor = image.getStack().getProcessor(frame).duplicate();
            processor.setRoi(roi);
            ImageStatistics stats = ImageStatistics.getStatistics(
                processor,
                Measurements.MEAN,
                calibration
            );
            values[frame - 1] = stats.mean;
        }
        return values;
    }

    private CurveAnalysis analyzeCurve(double[] rawCurve, AnalysisSettings settings) {
        double[] smoothedRaw = movingAverage(rawCurve, settings.smoothingRadius);
        int onsetIndex;
        int searchEndIndex;
        int direction;
        double noiseSigma;
        double riseThreshold;
        boolean automatic = settings.automatic;

        if (automatic) {
            UpslopeCandidate upslope = detectUpslope(smoothedRaw, settings);
            if (upslope == null) {
                return null;
            }
            onsetIndex = upslope.index;
            direction = upslope.direction;
            noiseSigma = upslope.noiseSigma;
            riseThreshold = upslope.riseThreshold;
            searchEndIndex = Math.min(
                rawCurve.length - 1,
                onsetIndex + firstPassFrameLimit(settings, rawCurve.length)
            );
        } else {
            onsetIndex = settings.manualStart - 1;
            searchEndIndex = settings.manualEnd - 1;
            noiseSigma = estimateLeadingNoise(smoothedRaw);
            riseThreshold = 0.0;
            direction = detectDirection(
                smoothedRaw,
                onsetIndex,
                searchEndIndex
            );
        }

        int baselineLength = onsetIndex > 0
            ? onsetIndex
            : Math.min(3, smoothedRaw.length);
        double baseline = median(Arrays.copyOf(smoothedRaw, baselineLength));
        double[] signal = new double[smoothedRaw.length];
        for (int i = 0; i < smoothedRaw.length; i++) {
            signal[i] = direction * (smoothedRaw[i] - baseline);
        }

        if (isFlat(signal, onsetIndex, searchEndIndex)) {
            return null;
        }

        PeakResult peak = findFirstMainPeak(
            signal,
            onsetIndex,
            searchEndIndex,
            settings.peakThresholdFraction
        );
        double peakAmplitude = signal[peak.index] - signal[onsetIndex];
        return new CurveAnalysis(
            signal,
            onsetIndex,
            searchEndIndex,
            direction,
            noiseSigma,
            riseThreshold,
            peakAmplitude,
            automatic,
            peak
        );
    }

    private UpslopeCandidate detectUpslope(
        double[] values,
        AnalysisSettings settings
    ) {
        double noiseSigma = estimateLeadingNoise(values);
        double robustRange = percentile(values, 95.0) - percentile(values, 5.0);
        if (!Double.isFinite(robustRange) || robustRange <= 1e-12) {
            return null;
        }

        double riseThreshold = Math.max(
            settings.noiseMultiplier * noiseSigma,
            RANGE_RISE_FRACTION * robustRange
        );
        double confirmationThreshold = Math.max(
            (settings.noiseMultiplier + 2.0) * noiseSigma,
            RANGE_CONFIRM_FRACTION * robustRange
        );
        int horizon = firstPassFrameLimit(settings, values.length);
        int minimumStart = Math.max(2, settings.smoothingRadius + 1);

        UpslopeCandidate positive = findUpslopeCandidate(
            values,
            1,
            minimumStart,
            settings.riseSpan,
            horizon,
            noiseSigma,
            riseThreshold,
            confirmationThreshold
        );
        UpslopeCandidate negative = findUpslopeCandidate(
            values,
            -1,
            minimumStart,
            settings.riseSpan,
            horizon,
            noiseSigma,
            riseThreshold,
            confirmationThreshold
        );

        UpslopeCandidate selected;
        if (positive == null) {
            selected = negative;
        } else if (negative == null) {
            selected = positive;
        } else if (positive.index < negative.index) {
            selected = positive;
        } else if (negative.index < positive.index) {
            selected = negative;
        } else {
            selected = positive.score >= negative.score ? positive : negative;
        }
        if (selected == null) {
            return null;
        }
        return new UpslopeCandidate(
            selected.index,
            selected.direction,
            selected.score,
            noiseSigma,
            riseThreshold
        );
    }

    private UpslopeCandidate findUpslopeCandidate(
        double[] values,
        int direction,
        int minimumStart,
        int riseSpan,
        int horizon,
        double noiseSigma,
        double riseThreshold,
        double confirmationThreshold
    ) {
        int lastStart = values.length - riseSpan - 1;
        for (int start = minimumStart; start <= lastStart; start++) {
            int positiveSteps = 0;
            boolean largeCounterStep = false;
            for (int i = start; i < start + riseSpan; i++) {
                double step = direction * (values[i + 1] - values[i]);
                if (step > 0.0) {
                    positiveSteps++;
                }
                if (step < -1.5 * noiseSigma) {
                    largeCounterStep = true;
                }
            }

            double netRise = direction * (values[start + riseSpan] - values[start]);
            int confirmationEnd = Math.min(values.length - 1, start + horizon);
            double futureRise = -Double.MAX_VALUE;
            for (int i = start + riseSpan; i <= confirmationEnd; i++) {
                futureRise = Math.max(
                    futureRise,
                    direction * (values[i] - values[start])
                );
            }

            if (
                positiveSteps >= riseSpan - 1 &&
                !largeCounterStep &&
                netRise >= riseThreshold &&
                futureRise >= confirmationThreshold
            ) {
                double score = futureRise / Math.max(noiseSigma, 1e-12);
                return new UpslopeCandidate(
                    start,
                    direction,
                    score,
                    noiseSigma,
                    riseThreshold
                );
            }
        }
        return null;
    }

    private double estimateLeadingNoise(double[] values) {
        int transitionCount = Math.min(
            values.length - 1,
            Math.max(8, (int) Math.round(values.length * 0.25))
        );
        if (transitionCount <= 0) {
            return 1e-12;
        }

        double[] differences = new double[transitionCount];
        for (int i = 0; i < transitionCount; i++) {
            differences[i] = values[i + 1] - values[i];
        }
        double center = median(differences);
        double[] deviations = new double[differences.length];
        for (int i = 0; i < differences.length; i++) {
            deviations[i] = Math.abs(differences[i] - center);
        }
        double sigma = MAD_TO_SIGMA * median(deviations);
        if (sigma <= 1e-12 || !Double.isFinite(sigma)) {
            double range = percentile(values, 95.0) - percentile(values, 5.0);
            sigma = Math.max(range * 0.005, 1e-12);
        }
        return sigma;
    }

    private int firstPassFrameLimit(AnalysisSettings settings, int frameCount) {
        if (settings.frameRate > 0.0) {
            return Math.max(
                settings.riseSpan + 2,
                (int) Math.round(settings.maximumFirstPassSeconds * settings.frameRate)
            );
        }
        return Math.min(
            frameCount - 1,
            Math.max(12, (int) Math.round(frameCount * 0.33))
        );
    }

    private int detectDirection(double[] values, int start, int end) {
        int baselineLength = start > 0 ? start : Math.min(3, values.length);
        double baseline = median(Arrays.copyOf(values, baselineLength));
        double max = -Double.MAX_VALUE;
        double min = Double.MAX_VALUE;
        for (int i = start; i <= end; i++) {
            double centered = values[i] - baseline;
            max = Math.max(max, centered);
            min = Math.min(min, centered);
        }
        return Math.abs(min) > Math.abs(max) ? -1 : 1;
    }

    private double[] movingAverage(double[] values, int radius) {
        if (radius == 0) {
            return Arrays.copyOf(values, values.length);
        }
        double[] smoothed = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            int from = Math.max(0, i - radius);
            int to = Math.min(values.length - 1, i + radius);
            double sum = 0.0;
            for (int j = from; j <= to; j++) {
                sum += values[j];
            }
            smoothed[i] = sum / (to - from + 1);
        }
        return smoothed;
    }

    private PeakResult findFirstMainPeak(
        double[] values,
        int start,
        int end,
        double thresholdFraction
    ) {
        int globalMaximum = start;
        for (int i = start + 1; i <= end; i++) {
            if (values[i] > values[globalMaximum]) {
                globalMaximum = i;
            }
        }

        double threshold = values[globalMaximum] * thresholdFraction;
        int first = -1;
        int qualifyingPeakCount = 0;
        for (int i = start + 1; i < end; i++) {
            boolean localMaximum =
                values[i] >= values[i - 1] &&
                values[i] >= values[i + 1] &&
                (values[i] > values[i - 1] || values[i] > values[i + 1]);
            if (localMaximum && values[i] >= threshold) {
                qualifyingPeakCount++;
                if (first < 0) {
                    first = i;
                }
            }
        }

        boolean usedFallback = false;
        if (first < 0) {
            first = globalMaximum;
            usedFallback = true;
        }
        return new PeakResult(first, qualifyingPeakCount, usedFallback);
    }

    private boolean isFlat(double[] values, int start, int end) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = start; i <= end; i++) {
            min = Math.min(min, values[i]);
            max = Math.max(max, values[i]);
        }
        return !Double.isFinite(min) ||
            !Double.isFinite(max) ||
            Math.abs(max - min) < 1e-12;
    }

    private double percentile(double[] values, double percent) {
        double[] copy = Arrays.copyOf(values, values.length);
        Arrays.sort(copy);
        double position = (percent / 100.0) * (copy.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) {
            return copy[lower];
        }
        double fraction = position - lower;
        return copy[lower] * (1.0 - fraction) + copy[upper] * fraction;
    }

    private double median(double[] values) {
        double[] copy = Arrays.copyOf(values, values.length);
        Arrays.sort(copy);
        int middle = copy.length / 2;
        if (copy.length % 2 == 0) {
            return (copy[middle - 1] + copy[middle]) / 2.0;
        }
        return copy[middle];
    }

    private ParabolicFit interpolatePeak(CurveAnalysis analysis) {
        int peakIndex = analysis.peak.index;
        if (peakIndex <= 0 || peakIndex >= analysis.signal.length - 1) {
            return ParabolicFit.notComputed("integer peak is at the sequence boundary");
        }

        double left = analysis.signal[peakIndex - 1];
        double center = analysis.signal[peakIndex];
        double right = analysis.signal[peakIndex + 1];
        if (!(center > left && center > right)) {
            return ParabolicFit.notComputed(
                "peak is flat or is not strictly higher than both neighboring frames"
            );
        }

        double denominator = left - 2.0 * center + right;
        if (!Double.isFinite(denominator) || denominator >= 0.0) {
            return ParabolicFit.notComputed("local peak is not concave downward");
        }

        double curvature = -denominator;
        double minimumCurvature = Math.max(0.25 * analysis.noiseSigma, 1e-12);
        if (curvature < minimumCurvature) {
            return ParabolicFit.notComputed(
                "peak curvature is too weak relative to estimated noise"
            );
        }

        double offset = 0.5 * (left - right) / denominator;
        if (!Double.isFinite(offset) || Math.abs(offset) > 0.5 + 1e-9) {
            return ParabolicFit.notComputed(
                "fitted vertex lies outside the central half-frame interval"
            );
        }

        double a = denominator / 2.0;
        double b = (right - left) / 2.0;
        double vertexValue = center + b * offset + a * offset * offset;
        double fractionalFrame = peakIndex + 1.0 + offset;

        String qualityReason = "";
        if (analysis.peak.usedFallback) {
            qualityReason = appendReason(
                qualityReason,
                "integer peak required a maximum-value fallback"
            );
        }
        if (analysis.peak.qualifyingPeakCount > 1) {
            qualityReason = appendReason(
                qualityReason,
                "multiple qualifying main peaks are present"
            );
        }
        if (analysis.peakAmplitude < 6.0 * Math.max(analysis.noiseSigma, 1e-12)) {
            qualityReason = appendReason(
                qualityReason,
                "peak-to-noise ratio is below 6"
            );
        }

        boolean accepted = qualityReason.isEmpty();
        String reportedReason = accepted ? LOCAL_FIT_ACCEPTED : qualityReason;
        if (searchMaximumAtEnd(analysis)) {
            reportedReason = appendReason(reportedReason, WINDOW_END_WARNING);
        }
        return new ParabolicFit(
            true,
            accepted,
            fractionalFrame,
            vertexValue,
            offset,
            curvature,
            reportedReason
        );
    }

    private boolean searchMaximumAtEnd(CurveAnalysis analysis) {
        double endValue = analysis.signal[analysis.searchEndIndex];
        if (!Double.isFinite(endValue)) return false;
        for (int i = analysis.onsetIndex; i < analysis.searchEndIndex; i++) {
            if (analysis.signal[i] > endValue) return false;
        }
        return true;
    }

    private String windowStatus(CurveAnalysis analysis) {
        return searchMaximumAtEnd(analysis)
            ? WINDOW_END_WARNING
            : "No end-boundary maximum detected (not proof of window completeness)";
    }

    private String localFitStatus(ParabolicFit fit) {
        if (fit.accepted) return LOCAL_FIT_ACCEPTED;
        return fit.computed ? "Local parabolic fit not used" : "Local parabolic fit not computed";
    }

    private ResultsTable createResultsTable(
        String name1,
        String name2,
        CurveAnalysis analysis1,
        CurveAnalysis analysis2,
        ParabolicFit fit1,
        ParabolicFit fit2
    ) {
        ResultsTable table = new ResultsTable();
        addResultRow(table, name1, "ROI 1: proximal/reference", analysis1, fit1);
        addResultRow(table, name2, "ROI 2: distal/comparison", analysis2, fit2);
        return table;
    }

    private void addResultRow(
        ResultsTable table,
        String name,
        String role,
        CurveAnalysis analysis,
        ParabolicFit fit
    ) {
        table.incrementCounter();
        table.addValue("Version", VERSION);
        table.addValue("ROI", name);
        table.addValue("Role", role);
        table.addValue("TTP_frame", analysis.peak.index + 1);
        table.addValue("Local_fit_status", localFitStatus(fit));
        table.addValue("Peak_selection_stability", PEAK_STABILITY_NOT_TESTED);
        table.addValue("Window_status", windowStatus(analysis));
        table.addValue("Local_fit_reason", fit.reason);
        table.addValue("Local_parabolic_candidate_frame", fit.computed ? fit.frame : Double.NaN);
        table.addValue("Search_start_frame", analysis.onsetIndex + 1);
        table.addValue("Search_end_frame", analysis.searchEndIndex + 1);
    }

    private String appendReason(String existing, String addition) {
        return existing.isEmpty() ? addition : existing + "; " + addition;
    }

    private void logResults(
        String name1,
        String name2,
        int ttp1,
        int ttp2,
        int deltaFrames,
        AnalysisSettings settings,
        CurveAnalysis analysis1,
        CurveAnalysis analysis2,
        ParabolicFit fit1,
        ParabolicFit fit2,
        int frameCount
    ) {
        IJ.log("=== " + TITLE + ": automatic first-upslope TTP ===");
        IJ.log("Mode: " + (settings.automatic ? "automatic" : "manual window"));
        IJ.log("Smoothing window: " + (settings.smoothingRadius * 2 + 1) + " points");
        IJ.log(
            "First main-peak threshold: " +
            format(settings.peakThresholdFraction * 100.0) + "%"
        );
        IJ.log(name1 + " detected upslope start (frame): " + (analysis1.onsetIndex + 1));
        IJ.log(name2 + " detected upslope start (frame): " + (analysis2.onsetIndex + 1));
        if (settings.automatic) {
            IJ.log(name1 + " leading-noise sigma: " + format(analysis1.noiseSigma));
            IJ.log(name2 + " leading-noise sigma: " + format(analysis2.noiseSigma));
        }
        IJ.log(name1 + " TTP (frame): " + ttp1);
        IJ.log(name2 + " TTP (frame): " + ttp2);
        IJ.log("Delta TTP, ROI 2 - ROI 1 (frames): " + signed(deltaFrames));
        IJ.log("Peak selection stability (both ROIs): " + PEAK_STABILITY_NOT_TESTED);

        if (settings.frameRate > 0.0) {
            IJ.log(name1 + " TTP (s): " + format((ttp1 - 1) / settings.frameRate));
            IJ.log(name2 + " TTP (s): " + format((ttp2 - 1) / settings.frameRate));
            IJ.log(
                "Delta TTP, ROI 2 - ROI 1 (s): " +
                signed(deltaFrames / settings.frameRate)
            );
        }

        if (settings.parabolicRefinement) {
            logParabolicFit(name1, fit1, settings.frameRate);
            logParabolicFit(name2, fit2, settings.frameRate);
            if (fit1.accepted && fit2.accepted) {
                double fittedDelta = fit2.frame - fit1.frame;
                IJ.log(
                    "Local parabolic Delta TTP candidate, ROI 2 - ROI 1 (frames): " +
                    signed(fittedDelta)
                );
                if (settings.frameRate > 0.0) {
                    IJ.log(
                        "Local parabolic Delta TTP candidate, ROI 2 - ROI 1 (s): " +
                        signed(fittedDelta / settings.frameRate)
                    );
                }
            } else {
                IJ.log(
                    "Parabolic Delta TTP: not reported because at least one ROI " +
                    "did not pass local fit-quality checks; the integer-frame estimate remains " +
                    "available, with peak selection stability not tested."
                );
            }
        }

        logCurveWarnings(name1, analysis1, frameCount, settings);
        logCurveWarnings(name2, analysis2, frameCount, settings);
        if (settings.automatic) {
            int allowedOnsetDifference = settings.frameRate > 0.0
                ? Math.max(3, (int) Math.round(settings.frameRate * 1.5))
                : 6;
            if (Math.abs(analysis1.onsetIndex - analysis2.onsetIndex) > allowedOnsetDifference) {
                IJ.log(
                    "WARNING: The two detected upslope starts are far apart; " +
                    "review the marked triangles and the source DSA."
                );
            }
            if (analysis1.direction != analysis2.direction) {
                IJ.log(
                    "WARNING: The two ROIs were detected with opposite signal directions; " +
                    "check subtraction polarity and ROI placement."
                );
            }
        }
    }

    private void logParabolicFit(
        String name,
        ParabolicFit fit,
        double frameRate
    ) {
        if (!fit.computed) {
            IJ.log(name + " parabolic TTP: not used - " + fit.reason + ".");
            return;
        }

        IJ.log(
            name + " parabolic candidate (frame): " + format(fit.frame) +
            "; offset: " + signed(fit.offset) +
            "; curvature: " + format(fit.curvature)
        );
        if (fit.accepted) {
            IJ.log(name + " fit status: " + localFitStatus(fit));
            IJ.log(name + " fit detail: " + fit.reason);
            if (frameRate > 0.0) {
                IJ.log(
                    name + " local parabolic TTP candidate (s): " +
                    format((fit.frame - 1.0) / frameRate)
                );
            }
        } else {
            IJ.log(
                name + " local parabolic fit: NOT USED - " + fit.reason +
                "; integer-frame TTP remains an estimate with peak selection stability not tested."
            );
        }
    }

    private void logCurveWarnings(
        String name,
        CurveAnalysis analysis,
        int frameCount,
        AnalysisSettings settings
    ) {
        if (analysis.peak.qualifyingPeakCount > 1) {
            IJ.log(
                "WARNING: " + name + " has " + analysis.peak.qualifyingPeakCount +
                " qualifying peaks; the first main peak was used."
            );
        } else if (analysis.peak.usedFallback) {
            IJ.log(
                "WARNING: " + name +
                " has no internal local peak; the largest value in the search period was used."
            );
        }
        if (analysis.peak.index >= analysis.searchEndIndex) {
            IJ.log(
                "WARNING: " + name +
                " peaks at the search boundary; increase maximum first-pass duration."
            );
        }
        if (searchMaximumAtEnd(analysis)) {
            IJ.log("WARNING: " + name + " - " + WINDOW_END_WARNING +
                ". This warning also applies when the selected local peak is inside the window; " +
                "review the source curve and search window.");
        }
        if (analysis.automatic && analysis.onsetIndex > Math.round(frameCount * 0.40)) {
            IJ.log(
                "WARNING: " + name +
                " upslope begins late in the sequence; visually verify the detected onset."
            );
        }
        if (
            analysis.automatic &&
            analysis.peakAmplitude < 6.0 * Math.max(analysis.noiseSigma, 1e-12)
        ) {
            IJ.log(
                "WARNING: " + name +
                " has a low peak-to-noise ratio; TTP may require manual review."
            );
        }
    }

    private double detectFrameRate(ImagePlus image) {
        Calibration calibration = image.getCalibration();
        if (
            calibration != null &&
            calibration.frameInterval > 0.0 &&
            !Double.isNaN(calibration.frameInterval)
        ) {
            return 1.0 / calibration.frameInterval;
        }
        Matcher matcher = FPS_IN_TITLE.matcher(image.getTitle());
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException ignored) {
                // Leave frame rate unknown when the title cannot be parsed.
            }
        }
        return 0.0;
    }

    private String roiName(RoiManager manager, int index, String fallback) {
        String name = manager.getName(index);
        return name == null || name.trim().isEmpty() ? fallback : name;
    }

    private void showComparisonPlot(
        CurveAnalysis analysis1,
        CurveAnalysis analysis2,
        String name1,
        String name2,
        int ttp1,
        int ttp2,
        ParabolicFit fit1,
        ParabolicFit fit2,
        AnalysisSettings settings
    ) {
        double[] frames = new double[analysis1.signal.length];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = i + 1;
        }

        Plot plot = new Plot(
            TITLE + " - Automatic First-upslope TTP",
            "Frame",
            "Baseline-corrected, smoothed signal"
        );
        plot.setColor(Color.RED);
        plot.add("line", frames, analysis1.signal);
        plot.setColor(Color.BLUE);
        plot.add("line", frames, analysis2.signal);
        plot.addLegend(name1 + "\n" + name2);

        plot.setLineWidth(2);
        plot.setColor(Color.RED);
        plot.addPoints(
            new double[] {analysis1.onsetIndex + 1},
            new double[] {analysis1.signal[analysis1.onsetIndex]},
            Plot.TRIANGLE
        );
        plot.setColor(Color.BLUE);
        plot.addPoints(
            new double[] {analysis2.onsetIndex + 1},
            new double[] {analysis2.signal[analysis2.onsetIndex]},
            Plot.TRIANGLE
        );

        plot.setLineWidth(3);
        plot.setColor(Color.RED);
        plot.addPoints(
            new double[] {ttp1},
            new double[] {analysis1.signal[ttp1 - 1]},
            Plot.CIRCLE
        );
        plot.setColor(Color.BLUE);
        plot.addPoints(
            new double[] {ttp2},
            new double[] {analysis2.signal[ttp2 - 1]},
            Plot.CIRCLE
        );

        if (fit1.accepted) {
            plot.setColor(Color.RED);
            plot.addPoints(
                new double[] {fit1.frame},
                new double[] {fit1.value},
                Plot.DIAMOND
            );
        }
        if (fit2.accepted) {
            plot.setColor(Color.BLUE);
            plot.addPoints(
                new double[] {fit2.frame},
                new double[] {fit2.value},
                Plot.DIAMOND
            );
        }

        plot.setLineWidth(1);
        plot.setColor(Color.BLACK);
        String fitLabel = fit1.accepted && fit2.accepted
            ? "; Local fit candidates " + format(fit1.frame) + " / " + format(fit2.frame) +
                "; Fit delta candidate " + signed(fit2.frame - fit1.frame)
            : "; Local fit delta not reported";
        plot.addLabel(
            0.02,
            0.06,
            "Rise " + (analysis1.onsetIndex + 1) + " / " +
            (analysis2.onsetIndex + 1) +
            "; TTP " + ttp1 + " / " + ttp2 +
            "; Delta " + signed(ttp2 - ttp1) + " frames" +
            fitLabel
        );
        plot.addLabel(0.02, 0.13, "Peak selection stability: Not tested (both ROIs)");
        if (searchMaximumAtEnd(analysis1) || searchMaximumAtEnd(analysis2)) {
            String affected = searchMaximumAtEnd(analysis1)
                ? (searchMaximumAtEnd(analysis2) ? "ROI 1 and ROI 2" : "ROI 1")
                : "ROI 2";
            plot.addLabel(0.02, 0.20, affected + ": window completeness unconfirmed (maximum at end)");
        }
        plot.show();
    }

    private String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private String signed(double value) {
        String formatted = format(value);
        return value > 0.0 ? "+" + formatted : formatted;
    }

    private String format(double value) {
        return IJ.d2s(value, 4);
    }

    private static final class AnalysisSettings {
        final boolean automatic;
        final boolean parabolicRefinement;
        final int manualStart;
        final int manualEnd;
        final int smoothingRadius;
        final int riseSpan;
        final double noiseMultiplier;
        final double peakThresholdFraction;
        final double maximumFirstPassSeconds;
        final double frameRate;

        AnalysisSettings(
            boolean automatic,
            boolean parabolicRefinement,
            int manualStart,
            int manualEnd,
            int smoothingRadius,
            int riseSpan,
            double noiseMultiplier,
            double peakThresholdFraction,
            double maximumFirstPassSeconds,
            double frameRate
        ) {
            this.automatic = automatic;
            this.parabolicRefinement = parabolicRefinement;
            this.manualStart = manualStart;
            this.manualEnd = manualEnd;
            this.smoothingRadius = smoothingRadius;
            this.riseSpan = riseSpan;
            this.noiseMultiplier = noiseMultiplier;
            this.peakThresholdFraction = peakThresholdFraction;
            this.maximumFirstPassSeconds = maximumFirstPassSeconds;
            this.frameRate = frameRate;
        }
    }

    private static final class UpslopeCandidate {
        final int index;
        final int direction;
        final double score;
        final double noiseSigma;
        final double riseThreshold;

        UpslopeCandidate(
            int index,
            int direction,
            double score,
            double noiseSigma,
            double riseThreshold
        ) {
            this.index = index;
            this.direction = direction;
            this.score = score;
            this.noiseSigma = noiseSigma;
            this.riseThreshold = riseThreshold;
        }
    }

    private static final class CurveAnalysis {
        final double[] signal;
        final int onsetIndex;
        final int searchEndIndex;
        final int direction;
        final double noiseSigma;
        final double riseThreshold;
        final double peakAmplitude;
        final boolean automatic;
        final PeakResult peak;

        CurveAnalysis(
            double[] signal,
            int onsetIndex,
            int searchEndIndex,
            int direction,
            double noiseSigma,
            double riseThreshold,
            double peakAmplitude,
            boolean automatic,
            PeakResult peak
        ) {
            this.signal = signal;
            this.onsetIndex = onsetIndex;
            this.searchEndIndex = searchEndIndex;
            this.direction = direction;
            this.noiseSigma = noiseSigma;
            this.riseThreshold = riseThreshold;
            this.peakAmplitude = peakAmplitude;
            this.automatic = automatic;
            this.peak = peak;
        }
    }

    private static final class PeakResult {
        final int index;
        final int qualifyingPeakCount;
        final boolean usedFallback;

        PeakResult(int index, int qualifyingPeakCount, boolean usedFallback) {
            this.index = index;
            this.qualifyingPeakCount = qualifyingPeakCount;
            this.usedFallback = usedFallback;
        }
    }

    private static final class ParabolicFit {
        final boolean computed;
        final boolean accepted;
        final double frame;
        final double value;
        final double offset;
        final double curvature;
        final String reason;

        ParabolicFit(
            boolean computed,
            boolean accepted,
            double frame,
            double value,
            double offset,
            double curvature,
            String reason
        ) {
            this.computed = computed;
            this.accepted = accepted;
            this.frame = frame;
            this.value = value;
            this.offset = offset;
            this.curvature = curvature;
            this.reason = reason;
        }

        static ParabolicFit notComputed(String reason) {
            return new ParabolicFit(
                false,
                false,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                reason
            );
        }
    }
}

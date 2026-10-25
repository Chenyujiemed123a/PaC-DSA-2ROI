import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Line;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.io.RoiDecoder;
import ij.io.RoiEncoder;
import ij.measure.ResultsTable;
import ij.process.ShortProcessor;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;

/** All numerical traces and pixels are generated mathematically, not from people. */
public class ReleaseTests {
    static final PaC_DSA_2ROI PLUGIN = new PaC_DSA_2ROI();
    static final Class<?> SETTINGS = nested("AnalysisSettings");
    static final Class<?> CURVE = nested("CurveAnalysis");
    static int passed;

    static Class<?> nested(String name) {
        try { return Class.forName("PaC_DSA_2ROI$" + name); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    static Object construct(Class<?> type, Object... args) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }
    static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    static Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = PaC_DSA_2ROI.class.getDeclaredMethod(name, types);
        method.setAccessible(true); return method.invoke(PLUGIN, args);
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static void pass(String message) { passed++; System.out.println("PASS " + message); }
    static Object settings(boolean automatic) throws Exception {
        return construct(SETTINGS, automatic, true, 1, 35, 1, 3, 3.0, .90, 3.0, 6.0);
    }
    static Object analyze(double[] values) throws Exception {
        return call("analyzeCurve", new Class<?>[]{double[].class, SETTINGS}, values, settings(true));
    }
    static int peak(Object curve) throws Exception {
        check(curve != null, "Expected a detected synthetic curve");
        return ((Number)field(field(curve, "peak"), "index")).intValue() + 1;
    }
    static Object fit(Object curve) throws Exception {
        return call("interpolatePeak", new Class<?>[]{CURVE}, curve);
    }
    static double pulse(double frame, double center, double amplitude) {
        double distance = (frame - center) / 3.0;
        return amplitude * Math.exp(-.5 * distance * distance);
    }
    static double[] trace(double center, int sign, boolean later) {
        double[] values = new double[69];
        for (int i=0; i<values.length; i++)
            values[i] = 2000 + sign * (pulse(i+1, center, 800) + (later ? pulse(i+1, 48, 1500) : 0));
        return values;
    }
    static void selectionAndGeometry() {
        check(Arrays.equals(PaC_DSA_2ROI.selectRoiIndices(2, null), new int[]{0,1}), "Two ROIs");
        int[] selected = {7,3};
        check(Arrays.equals(PaC_DSA_2ROI.selectRoiIndices(8, selected), new int[]{3,7}), "Manager ordering");
        check(Arrays.equals(selected, new int[]{7,3}), "Selection mutated");
        for (int[] invalid : new int[][]{ {}, {0}, {0,1,2}, {1,1}, {-1,2}, {0,8} }) {
            boolean rejected=false;
            try { PaC_DSA_2ROI.selectRoiIndices(8, invalid); }
            catch (IllegalArgumentException expected) { rejected=true; }
            check(rejected, "Ambiguous selection accepted");
        }
        pass("ROI selection, ordering, and rejection");
        check(PaC_DSA_2ROI.roiValidationError(new Roi(0,0,128,128),128,128)==null, "Valid ROI rejected");
        for (Roi invalid : new Roi[]{null, new Line(1,1,3,3), new PointRoi(1,1),
                new Roi(-1,2,10,10), new Roi(120,120,10,10)})
            check(PaC_DSA_2ROI.roiValidationError(invalid,128,128)!=null, "Invalid geometry accepted");
        pass("Area ROI geometry validation");
    }
    static void curves() throws Exception {
        for (int sign : new int[]{1,-1}) {
            Object proximal=analyze(trace(17,sign,false)), distal=analyze(trace(19,sign,false));
            check(peak(proximal)==17 && peak(distal)==19, "Known peak locations");
            check((Integer)field(proximal,"direction")==sign, "Polarity detection");
            check(peak(distal)-peak(proximal)==2, "Known two-frame delay");
        }
        pass("Positive and negative polarity: TTP 17/19, delta +2 frames");
        double[] input=trace(17,1,false), original=input.clone(); analyze(input);
        check(Arrays.equals(input,original),"Input trace modified");
        pass("Analysis does not mutate input trace");
        check(analyze(new double[69])==null, "Flat signal must not produce a peak");
        pass("Flat signal rejected");
        check(peak(analyze(trace(17,1,true)))==17,"Later larger peak selected");
        pass("Later synthetic recirculation-like peak excluded by first-pass window");
        Object clean=analyze(trace(17,1,false));
        check((Boolean)field(fit(clean),"accepted"),"Clean fit rejected");
        double[] plateau=new double[69];
        for(int i=9;i<14;i++) plateau[i]=(i-8)*100;
        for(int i=14;i<21;i++) plateau[i]=500;
        for(int i=21;i<26;i++) plateau[i]=(26-i)*100;
        check(!(Boolean)field(fit(analyze(plateau)),"accepted"),"Plateau fit accepted");
        pass("Local parabolic fit accepted for clean peak and rejected for plateau");
        Object manual=call("analyzeCurve",new Class<?>[]{double[].class,SETTINGS},trace(17,1,false),settings(false));
        check(peak(manual)==17,"Manual-window TTP mismatch");
        pass("Manual window preserves known peak");
    }
    static void qualitySemantics() throws Exception {
        double[] signal={0,0,0,1,4,10,4,1,3,5,8,10.5};
        Object selected=construct(nested("PeakResult"),5,1,false);
        Object boundary=construct(CURVE,signal,3,11,1,.01,.03,10.0,true,selected);
        Object clean=analyze(trace(17,1,false));
        check((Boolean)call("searchMaximumAtEnd",new Class<?>[]{CURVE},boundary),"Missing boundary warning");
        Object bf=fit(boundary), cf=fit(clean);
        check((Boolean)field(bf,"accepted"),"Synthetic interior local fit should pass");
        check(((String)field(bf,"reason")).contains("Window completeness unconfirmed"),"Missing fit limitation");
        ResultsTable table=(ResultsTable)call("createResultsTable",
            new Class<?>[]{String.class,String.class,CURVE,CURVE,nested("ParabolicFit"),nested("ParabolicFit")},
            "synthetic_boundary","synthetic_clean",boundary,clean,bf,cf);
        check(table.size()==2,"Expected two result rows");
        for(int i=0;i<2;i++) check(table.getStringValue("Peak_selection_stability",i).equals("Not tested"),"False stability claim");
        check(table.getStringValue("Window_status",0).contains("Window completeness unconfirmed"),"Missing results warning");
        pass("Local fit, window warning, and untested peak stability remain distinct");
    }
    static void imageExample(Path directory) throws Exception {
        Files.createDirectories(directory);
        ImageStack stack=new ImageStack(128,128);
        for(int frame=1;frame<=69;frame++) {
            short[] pixels=new short[128*128]; Arrays.fill(pixels,(short)2000);
            for(int y=16;y<112;y++) for(int x=44;x<76;x++) {
                double delay=2.0*(90-y)/56.0;
                pixels[y*128+x]=(short)Math.round(2000+pulse(frame,17+delay,800));
            }
            stack.addSlice(new ShortProcessor(128,128,pixels,null));
        }
        ImagePlus image=new ImagePlus("SYNTHETIC_6_FPS",stack);
        image.getCalibration().frameInterval=1.0/6.0;
        image.getCalibration().setTimeUnit("sec");
        Path tiff=directory.resolve("synthetic_6fps.tif");
        check(new FileSaver(image).saveAsTiffStack(tiff.toString()),"TIFF save failed");
        Roi proximal=new Roi(48,84,12,12), distal=new Roi(48,28,12,12);
        proximal.setName("01_proximal_synthetic"); distal.setName("02_distal_synthetic");
        Path zip=directory.resolve("synthetic_ROIs.zip");
        try(ZipOutputStream archive=new ZipOutputStream(Files.newOutputStream(zip))) {
            for(Roi roi:new Roi[]{proximal,distal}) {
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                new RoiEncoder(bytes).write(roi);
                ZipEntry entry=new ZipEntry(roi.getName()+".roi"); entry.setTime(1792368000000L);
                archive.putNextEntry(entry); archive.write(bytes.toByteArray()); archive.closeEntry();
            }
        }
        ImagePlus reopened=new Opener().openImage(tiff.toString());
        check(reopened!=null && reopened.getStackSize()==69,"TIFF round-trip");
        List<Roi> rois=new ArrayList<Roi>();
        try(ZipFile archive=new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries=archive.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry=entries.nextElement();
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream stream=archive.getInputStream(entry)) {
                    byte[] buffer=new byte[512]; int n;
                    while((n=stream.read(buffer))!=-1) bytes.write(buffer,0,n);
                }
                rois.add(new RoiDecoder(bytes.toByteArray(),entry.getName()).getRoi());
            }
        }
        check(rois.size()==2,"ROI ZIP round-trip");
        double[] a=(double[])call("measureMeanCurve",new Class<?>[]{ImagePlus.class,Roi.class},reopened,rois.get(0));
        double[] b=(double[])call("measureMeanCurve",new Class<?>[]{ImagePlus.class,Roi.class},reopened,rois.get(1));
        int first=peak(analyze(a)), second=peak(analyze(b));
        check(first==17 && second==19,"TIFF/ROI integration expected 17/19, got "+first+"/"+second);
        StringBuilder csv=new StringBuilder("frame,proximal_mean,distal_mean\n");
        for(int i=0;i<a.length;i++) csv.append(i+1).append(',').append(a[i]).append(',').append(b[i]).append('\n');
        Files.write(directory.resolve("synthetic_curves.csv"),csv.toString().getBytes(StandardCharsets.UTF_8));
        String expected="{\n  \"data_origin\": \"Entirely mathematical; no patient data\",\n"
            +"  \"frames\": 69, \"fps\": 6, \"TTP1_frame\": 17, \"TTP2_frame\": 19,\n"
            +"  \"delta_frames\": 2, \"delta_seconds\": 0.3333333333333333,\n"
            +"  \"peak_selection_stability\": \"Not tested\"\n}\n";
        Files.write(directory.resolve("expected_results.json"),expected.getBytes(StandardCharsets.UTF_8));
        image.close(); reopened.close();
        pass("Synthetic TIFF + saved ROI ZIP round-trip: TTP 17/19, delta +2 frames = 1/3 s");
    }
    public static void main(String[] args) throws Exception {
        selectionAndGeometry(); curves(); qualitySemantics(); imageExample(Paths.get(args[0]));
        System.out.println("ALL "+passed+" TEST GROUPS PASSED (synthetic data only)");
    }
}

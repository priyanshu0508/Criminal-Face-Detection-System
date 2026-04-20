package cfds.core;

import cfds.alert.AlertManager;
import cfds.database.CriminalRecord;
import cfds.database.SQLiteDatabase;
import cfds.detection.YuNetDetector;
import cfds.recognition.SFaceRecognizer;
import cfds.utils.Config;
import cfds.utils.Logger;
import cfds.utils.Profiler;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_highgui;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.Comparator;

public class Engine {
    private boolean running = false;
    private final BlockingQueue<FrameData> captureQueue = new ArrayBlockingQueue<>(4);
    private final BlockingQueue<FrameData> displayQueue = new ArrayBlockingQueue<>(4);

    private SQLiteDatabase database;
    private YuNetDetector detector;
    private SFaceRecognizer recognizer;
    private AlertManager alertManager;
    private List<CriminalRecord> criminalCache;
    private float matchThreshold;

    public boolean initialize(String configPath) {
        Config cfg = Config.getInstance();
        if (!cfg.load(configPath)) {
            System.err.println("Failed to load config: " + configPath);
            return false;
        }

        String logLvlStr = cfg.getString("system", "log_level", "INFO");
        Logger.Level level = Logger.Level.INFO;
        if (logLvlStr.equals("DEBUG")) level = Logger.Level.DEBUG;
        Logger.getInstance().init(cfg.getString("system", "log_dir", "logs/"), level);

        Logger.info("Java CFDS Engine Initializing...");

        database = new SQLiteDatabase();
        if (!database.connect(cfg.getString("database", "path", "data/cfds.db"))) return false;
        database.initializeSchema();
        reloadCriminalCache();

        detector = new YuNetDetector();
        int[] detInput = cfg.getIntArray("detection", "input_size", new int[]{320, 320});
        if (!detector.initialize(
                cfg.getString("detection", "model_path", ""),
                cfg.getFloat("detection", "confidence_threshold", 0.8f),
                cfg.getFloat("detection", "nms_threshold", 0.3f),
                detInput[0], detInput[1]
        )) return false;

        recognizer = new SFaceRecognizer();
        if (!recognizer.initialize(cfg.getString("recognition", "model_path", ""))) return false;

        alertManager = new AlertManager();
        alertManager.initialize(
                cfg.getString("alert", "log_path", "logs/alerts.jsonl"),
                cfg.getInt("alert", "cooldown_seconds", 5),
                cfg.getBoolean("alert", "visual_banner", true)
        );

        matchThreshold = cfg.getFloat("recognition", "cosine_threshold", 0.363f);

        Logger.info("Java CFDS Engine Initialized Successfully.");
        return true;
    }

    private void reloadCriminalCache() {
        if (database != null) {
            criminalCache = database.fetchAllEmbeddings();
            Logger.info("Loaded " + criminalCache.size() + " criminal records into cache.");
        }
    }

    private void threadCapture(int deviceId) {
        Logger.info("Capture thread started for device: " + deviceId);
        VideoCapture cap = new VideoCapture(deviceId);
        if (!cap.isOpened()) {
            Logger.error("Failed to open camera device: " + deviceId);
            running = false;
            return;
        }

        int frameId = 0;
        while (running) {
            FrameData data = new FrameData();
            data.frame = new Mat();
            cap.read(data.frame);
            if (data.frame.empty()) {
                Logger.warning("Empty frame received");
                continue;
            }
            data.frame_id = ++frameId;
            try {
                if (!captureQueue.offer(data, 100, TimeUnit.MILLISECONDS)) {
                    data.frame.release();
                }
            } catch (InterruptedException e) {
                break;
            }
        }
        cap.release();
        Logger.info("Capture thread terminating.");
    }

    private void threadProcess() {
        Logger.info("Processing thread started.");
        while (running) {
            try {
                FrameData data = captureQueue.poll(100, TimeUnit.MILLISECONDS);
                if (data == null) continue;

                Mat detectedFaces = detector.detect(data.frame);

                if (detectedFaces != null && !detectedFaces.empty()) {
                    for (int i = 0; i < detectedFaces.rows(); i++) {
                        Mat faceRow = detectedFaces.row(i);
                        FrameData.ProcessedFace pf = new FrameData.ProcessedFace();
                        pf.detect_info = faceRow.clone();

                        float[] embedding = recognizer.extractFeature(data.frame, faceRow);
                        if (embedding.length > 0) {
                            float bestScore = 0.0f;
                            CriminalRecord bestMatchRec = null;

                            for (CriminalRecord cachedRec : criminalCache) {
                                float score = recognizer.match(embedding, cachedRec.embedding);
                                if (score > bestScore) {
                                    bestScore = score;
                                    bestMatchRec = cachedRec;
                                }
                            }

                            if (bestScore >= matchThreshold && bestMatchRec != null) {
                                pf.is_match = true;
                                cfds.database.SearchResult sr = new cfds.database.SearchResult();
                                sr.record = bestMatchRec;
                                sr.cosine_similarity = bestScore;
                                pf.match_result = sr;
                                alertManager.triggerAlert(sr);
                            }
                        }
                        data.faces.add(pf);
                    }
                    detectedFaces.release(); // Explicitly release detection results
                }
                if (!displayQueue.offer(data, 100, TimeUnit.MILLISECONDS)) {
                    data.frame.release();
                    for (FrameData.ProcessedFace face : data.faces) {
                        if (face.detect_info != null) face.detect_info.release();
                    }
                }
            } catch (InterruptedException e) {
                break;
            }
        }
        Logger.info("Processing thread terminating.");
    }

    private void threadDisplay() {
        Logger.info("Display thread started.");
        String winName = Config.getInstance().getString("display", "window_title", "CFDS (Java)");
        opencv_highgui.namedWindow(winName, opencv_highgui.WINDOW_AUTOSIZE);
        boolean showFps = Config.getInstance().getBoolean("display", "show_fps", true);

        while (running) {
            try {
                FrameData data = displayQueue.poll(100, TimeUnit.MILLISECONDS);
                if (data == null) {
                    opencv_highgui.waitKey(1);
                    continue;
                }
                Profiler.getInstance().tick();
                drawResults(data.frame, data);

                if (showFps) {
                    String stats = Profiler.getInstance().getStatsString();
                    opencv_imgproc.putText(data.frame, stats, new Point(10, 85),
                            opencv_imgproc.FONT_HERSHEY_SIMPLEX, 0.6, new Scalar(0, 255, 0, 0), 2, opencv_imgproc.LINE_8, false);
                }

                opencv_highgui.imshow(winName, data.frame);
                
                // Cleanup native MATs to prevent memory leak
                data.frame.release();
                for (FrameData.ProcessedFace face : data.faces) {
                    if (face.detect_info != null) face.detect_info.release();
                }

                int key = opencv_highgui.waitKey(1);
                if (key == 27 || key == 'q' || key == 'Q') {
                    running = false;
                }
            } catch (InterruptedException e) {
                break;
            }
        }
        opencv_highgui.destroyWindow(winName);
        Logger.info("Display thread terminating.");
    }

    private void drawResults(Mat frame, FrameData data) {
        boolean alertActive = false;
        cfds.database.SearchResult highestAlert = null;

        for (FrameData.ProcessedFace pf : data.faces) {
            // Blue/Green logic adapted to user request (Green for innocent)
            Scalar color = pf.is_match ? new Scalar(0, 0, 255, 0) : new Scalar(0, 255, 0, 0);
            int thickness = pf.is_match ? 3 : 2;

            // Extract bbox from native Mat [x, y, w, h]
            org.bytedeco.javacpp.indexer.FloatIndexer idx = pf.detect_info.createIndexer();
            int x = (int) idx.get(0, 0);
            int y = (int) idx.get(0, 1);
            int w = (int) idx.get(0, 2);
            int h = (int) idx.get(0, 3);
            idx.release();

            Rect bbox = new Rect(x, y, w, h);
            opencv_imgproc.rectangle(frame, bbox, color, thickness, opencv_imgproc.LINE_8, 0);

            if (pf.is_match) {
                alertActive = true;
                if (highestAlert == null || pf.match_result.cosine_similarity > highestAlert.cosine_similarity) {
                    highestAlert = pf.match_result;
                }
                String label = pf.match_result.record.name + " (" + (int)(pf.match_result.cosine_similarity * 100) + "%)";
                opencv_imgproc.putText(frame, label, new Point(x, Math.max(0, y - 10)),
                        opencv_imgproc.FONT_HERSHEY_SIMPLEX, 0.6, color, 2, opencv_imgproc.LINE_8, false);
            }
        }

        if (alertActive && highestAlert != null) {
            alertManager.drawAlertBanner(frame, highestAlert);
        }
    }

    public void runLiveDetection(int deviceId) {
        if (detector == null || recognizer == null) return;
        running = true;
        captureQueue.clear();
        displayQueue.clear();

        Thread tCapture = new Thread(() -> threadCapture(deviceId), "Capture");
        Thread tProcess = new Thread(this::threadProcess, "Process");
        Thread tDisplay = new Thread(this::threadDisplay, "Display");

        tCapture.start();
        tProcess.start();
        tDisplay.start();

        try {
            tDisplay.join();
            running = false;
            tCapture.join();
            tProcess.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void processSingleImage(String inputPath, String outputPath) {
        Mat frame = opencv_imgcodecs.imread(inputPath);
        if (frame.empty()) {
            Logger.error("Could not read image: " + inputPath);
            return;
        }

        FrameData data = new FrameData();
        data.frame = frame.clone();

        Mat detected = detector.detect(data.frame);
        if (detected != null && !detected.empty()) {
            for (int i = 0; i < detected.rows(); i++) {
                Mat faceRow = detected.row(i);
                FrameData.ProcessedFace pf = new FrameData.ProcessedFace();
                pf.detect_info = faceRow.clone();

                float[] embedding = recognizer.extractFeature(data.frame, faceRow);
                if (embedding.length > 0) {
                    float bestScore = 0.0f;
                    CriminalRecord bestMatchRec = null;

                    for (CriminalRecord cachedRec : criminalCache) {
                        float score = recognizer.match(embedding, cachedRec.embedding);
                        if (score > bestScore) {
                            bestScore = score;
                            bestMatchRec = cachedRec;
                        }
                    }

                    if (bestScore >= matchThreshold && bestMatchRec != null) {
                        pf.is_match = true;
                        cfds.database.SearchResult sr = new cfds.database.SearchResult();
                        sr.record = bestMatchRec;
                        sr.cosine_similarity = bestScore;
                        pf.match_result = sr;
                        alertManager.triggerAlert(sr);
                    }
                }
                data.faces.add(pf);
            }
        }

        drawResults(data.frame, data);

        if (outputPath != null && !outputPath.isEmpty()) {
            opencv_imgcodecs.imwrite(outputPath, data.frame);
            Logger.info("Processed image saved to: " + outputPath);
        } else {
            opencv_highgui.imshow("Result", data.frame);
            opencv_highgui.waitKey(0);
        }
    }

    public boolean enrollCriminal(String name, String criminal_id, String crime, String imagePath) {
        Mat frame = opencv_imgcodecs.imread(imagePath);
        if (frame.empty()) {
            Logger.error("Invalid image: " + imagePath);
            return false;
        }

        Mat detected = detector.detect(frame);
        if (detected == null || detected.empty()) {
            Logger.error("No face found in image: " + imagePath);
            return false;
        }

        // Just take the first face for now (row 0)
        Mat faceRow = detected.row(0);
        float[] embedding = recognizer.extractFeature(frame, faceRow);

        if (embedding.length == 0) {
            Logger.error("Failed to extract embedding.");
            return false;
        }

        CriminalRecord rec = new CriminalRecord();
        rec.name = name;
        rec.criminal_id = criminal_id;
        rec.default_crime = crime;
        rec.image_path = imagePath;
        rec.embedding = embedding;

        if (database.insertRecord(rec)) {
            reloadCriminalCache();
            Logger.info("Successfully enrolled criminal: " + name);
            return true;
        }
        return false;
    }

    public void listCriminals() {
        var records = database.getAllRecords();
        System.out.println("\n============================================");
        System.out.println("      ENROLLED CRIMINALS (" + records.size() + ")");
        System.out.println("============================================");
        for (CriminalRecord r : records) {
            System.out.println("[" + r.criminal_id + "] " + r.name + " | Crime: " + r.default_crime);
        }
        System.out.println("============================================\n");
    }

    public boolean deleteCriminal(String criminal_id) {
        if (database.deleteRecord(criminal_id)) {
            reloadCriminalCache();
            return true;
        }
        return false;
    }
}

package cfds.alert;

import cfds.database.SearchResult;
import cfds.utils.Logger;
import com.google.gson.JsonObject;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.global.opencv_imgproc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class AlertManager {
    private String logPath;
    private int cooldownSeconds;
    private boolean visualBanner;
    
    private static class AlertHistory {
        LocalDateTime lastTriggerTime;
        int triggerCount;
        AlertHistory(LocalDateTime t) { lastTriggerTime = t; triggerCount = 1; }
    }
    
    private final Map<String, AlertHistory> alertHistory = new HashMap<>();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public synchronized void initialize(String logPath, int cooldownSeconds, boolean visualBanner) {
        this.logPath = logPath;
        this.cooldownSeconds = cooldownSeconds;
        this.visualBanner = visualBanner;
        
        File file = new File(logPath);
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        Logger.info("AlertManager initialized.");
    }

    public synchronized boolean triggerAlert(SearchResult result) {
        LocalDateTime now = LocalDateTime.now();
        
        if (alertHistory.containsKey(result.record.criminal_id)) {
            AlertHistory hist = alertHistory.get(result.record.criminal_id);
            if (java.time.Duration.between(hist.lastTriggerTime, now).getSeconds() < cooldownSeconds) {
                return false; // Still in cooldown
            }
            hist.lastTriggerTime = now;
            hist.triggerCount++;
        } else {
            alertHistory.put(result.record.criminal_id, new AlertHistory(now));
        }

        JsonObject logEntry = new JsonObject();
        logEntry.addProperty("timestamp", now.format(timeFormatter));
        logEntry.addProperty("event", "CRIMINAL_DETECTED");
        logEntry.addProperty("criminal_id", result.record.criminal_id);
        logEntry.addProperty("name", result.record.name);
        logEntry.addProperty("crime", result.record.default_crime);
        logEntry.addProperty("confidence", result.cosine_similarity);

        try (PrintWriter pw = new PrintWriter(new FileWriter(logPath, true), true)) {
            pw.println(logEntry.toString());
        } catch (IOException e) {
            Logger.error("Failed to write alert log: " + e.getMessage());
        }

        Logger.critical(">>> ALERT TRIPPED: " + result.record.name + " (" + result.record.default_crime + ") <<<");
        return true;
    }

    public void drawAlertBanner(Mat frame, SearchResult result) {
        if (!visualBanner || frame == null || frame.empty()) return;

        // Draw a red banner across the top
        Rect bannerRect = new Rect(0, 0, frame.cols(), 60);
        
        // Semi-transparent overlay
        Mat overlay = new Mat();
        frame.copyTo(overlay);
        opencv_imgproc.rectangle(overlay, bannerRect, new Scalar(0, 0, 200, 0), opencv_imgproc.FILLED, opencv_imgproc.LINE_8, 0);
        org.bytedeco.opencv.global.opencv_core.addWeighted(overlay, 0.7, frame, 0.3, 0, frame);
        overlay.release();

        String title = "! WARNING: WANTED CRIMINAL DETECTED !";
        int[] baseline = new int[1];
        Size titleSize = opencv_imgproc.getTextSize(title, opencv_imgproc.FONT_HERSHEY_DUPLEX, 0.8, 2, baseline);
        opencv_imgproc.putText(frame, title, new Point((frame.cols() - titleSize.width()) / 2, 25),
                opencv_imgproc.FONT_HERSHEY_DUPLEX, 0.8, new Scalar(255, 255, 255, 0), 2, opencv_imgproc.LINE_AA, false);

        String details = String.format("Name: %s | Crime: %s | Match: %.1f%%",
                result.record.name, result.record.default_crime, result.cosine_similarity * 100.0f);
        Size detSize = opencv_imgproc.getTextSize(details, opencv_imgproc.FONT_HERSHEY_COMPLEX_SMALL, 0.8, 1, baseline);
        opencv_imgproc.putText(frame, details, new Point((frame.cols() - detSize.width()) / 2, 50),
                opencv_imgproc.FONT_HERSHEY_COMPLEX_SMALL, 0.8, new Scalar(200, 255, 255, 0), 1, opencv_imgproc.LINE_AA, false);
    }
}

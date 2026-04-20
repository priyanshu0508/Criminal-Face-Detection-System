package cfds.detection;

import cfds.utils.Logger;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_objdetect.FaceDetectorYN;

import java.io.File;

public class YuNetDetector {
    private FaceDetectorYN faceDetector;

    public boolean initialize(String modelPath, float scoreThreshold, float nmsThreshold, int width, int height) {
        if (!new File(modelPath).exists()) {
            Logger.error("YuNet model not found: " + modelPath);
            return false;
        }
        try {
            faceDetector = FaceDetectorYN.create(modelPath, "", new Size(width, height), scoreThreshold, nmsThreshold, 5000, 0, 0);
            Logger.info("YuNet Detector Initialized: " + width + "x" + height);
            return true;
        } catch (Exception e) {
            Logger.error("Failed to initialize YuNet: " + e.getMessage());
            return false;
        }
    }

    public void setInputSize(int width, int height) {
        if (faceDetector != null) {
            faceDetector.setInputSize(new Size(width, height));
        }
    }

    /**
     * Detects faces. The returned Mat contains rows of [x, y, w, h, x_re, y_re, x_le, y_le, x_nt, y_nt, x_rcm, y_rcm, x_lcm, y_lcm, score].
     */
    public Mat detect(Mat image) {
        if (faceDetector == null || image == null || image.empty()) {
            return new Mat();
        }
        
        // Ensure image fits the detector input size or input size adapts to image
        setInputSize(image.cols(), image.rows());
        
        Mat faces = new Mat();
        faceDetector.detect(image, faces);
        return faces;
    }
}

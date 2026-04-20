package cfds.recognition;

import cfds.utils.Logger;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_objdetect.FaceRecognizerSF;

import java.io.File;

public class SFaceRecognizer {
    private FaceRecognizerSF faceRecognizer;

    public boolean initialize(String modelPath) {
        if (!new File(modelPath).exists()) {
            Logger.error("SFace model not found: " + modelPath);
            return false;
        }
        try {
            faceRecognizer = FaceRecognizerSF.create(modelPath, "");
            Logger.info("SFace Recognizer Initialized.");
            return true;
        } catch (Exception e) {
            Logger.error("Failed to initialize SFace: " + e.getMessage());
            return false;
        }
    }

    public float[] extractFeature(Mat image, Mat faceRow) {
        if (faceRecognizer == null || image == null || image.empty() || faceRow == null || faceRow.empty()) {
            return new float[0];
        }

        try {
            Mat alignedFace = new Mat();
            faceRecognizer.alignCrop(image, faceRow, alignedFace);

            Mat feature = new Mat();
            faceRecognizer.feature(alignedFace, feature);
            
            // Feature is 1x128 CV_32FC1
            float[] result = new float[128];
            FloatIndexer indexer = feature.createIndexer();
            for (int i = 0; i < 128; i++) {
                result[i] = indexer.get(0, i);
            }
            indexer.release(); // release native memory binding
            return result;
        } catch (Exception e) {
            Logger.error("Error extracting feature: " + e.getMessage());
            return new float[0];
        }
    }

    public float match(float[] embedding1, float[] embedding2) {
        if (embedding1.length != 128 || embedding2.length != 128 || faceRecognizer == null) {
            return 0.0f;
        }
        
        Mat m1 = new Mat(1, 128, org.bytedeco.opencv.global.opencv_core.CV_32F);
        Mat m2 = new Mat(1, 128, org.bytedeco.opencv.global.opencv_core.CV_32F);
        
        FloatIndexer idx1 = m1.createIndexer();
        FloatIndexer idx2 = m2.createIndexer();
        for (int i = 0; i < 128; i++) {
            idx1.put(0, i, embedding1[i]);
            idx2.put(0, i, embedding2[i]);
        }
        idx1.release();
        idx2.release();
        
        // FaceRecognizerSF.FR_COSINE = 0
        return (float) faceRecognizer.match(m1, m2, FaceRecognizerSF.FR_COSINE);
    }
}

package cfds.core;

import cfds.database.SearchResult;
import org.bytedeco.opencv.opencv_core.Mat;
import java.util.ArrayList;
import java.util.List;

public class FrameData {
    public int frame_id;
    public Mat frame; // Original or drawn image
    public List<ProcessedFace> faces = new ArrayList<>();
    
    public static class ProcessedFace {
        public Mat detect_info; // Single row from detection containing bbox and landmarks
        public boolean is_match = false;
        public SearchResult match_result;
    }
}

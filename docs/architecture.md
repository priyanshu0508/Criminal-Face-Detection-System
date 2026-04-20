# CFDS Java Architecture

## 1. System Overview
The Criminal Face Detection System (CFDS) is a high-performance Java application designed for real-time video surveillance and facial identification. It maps a 3-thread producer-consumer architecture directly into the JVM.

## 2. Component Design

### 📦 cfds.core.Engine
The heart of the system. It handles multithreading using `ArrayBlockingQueue` to pass data between three dedicated threads:
*   **Capture Thread**: Reads raw frames from the `VideoCapture` hardware.
*   **Process Thread**: Performs DNN inference (Detection + Recognition) and Database matching.
*   **Display Thread**: Handles GUI rendering and visual alerts.

### 📦 cfds.detection.YuNetDetector
A JNI wrapper around the **YuNet CNN**. This model is optimized for edge detection, providing 5 landmarks and a bounding box for every face at high speed.

### 📦 cfds.recognition.SFaceRecognizer
A JNI wrapper around **SFace (ArcFace)**. It transforms a cropped facial image into a unique 128-dimensional floating-point vector (Embedding).

### 📦 cfds.database.SQLiteDatabase
A thread-safe persistence layer using **JDBC**. It stores criminal metadata and facial embeddings as `BLOB` data. Uses **Write-Ahead Logging (WAL)** for high concurrency.

## 3. Data Flow
1.  **Frame Acquisition**: Camera -> `Engine` (Capture Thread)
2.  **Detection**: `YuNet` -> Bounding Boxes
3.  **Recognition**: `SFace` -> Feature Extraction (128 floats)
4.  **Matching**: Embedding -> Math Comparison (Cosine Similarity) -> `SQLite` Search
5.  **Alerting**: Match Found -> `AlertManager` -> Visual UI Banner + JSON Log

## 4. Environmental Isolation & Portability
The project is designed to be fully functional even in restricted environments (e.g., systems with a full C: drive). 

*   **Custom Local Repository**: Maven is forced to use `D:\CFDS\.m2_repo` via a custom `settings.xml`.
*   **Native Library Cache**: At runtime, `JavaCV` extracts native DLLs to `D:\CFDS\.javacpp_cache` using the `org.bytedeco.javacpp.cachedir` system property.
*   **Temp Directory**: Java's default temporary directory and database driver extraction are redirected to `D:\CFDS\temp` using the `java.io.tmpdir` system property.

This isolation ensures that the application remains high-performance and avoids all "Disk Full" errors during both build-time and run-time phases.

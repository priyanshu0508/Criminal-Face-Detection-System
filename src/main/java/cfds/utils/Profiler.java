package cfds.utils;

public class Profiler {
    private static Profiler instance = null;
    private long lastTime = System.nanoTime();
    private int frames = 0;
    private double currentFps = 0.0;
    
    private Profiler() {}
    
    public static synchronized Profiler getInstance() {
        if (instance == null) {
            instance = new Profiler();
        }
        return instance;
    }
    
    public void tick() {
        frames++;
        long now = System.nanoTime();
        if (now - lastTime >= 1_000_000_000L) { // 1 second
            currentFps = frames;
            frames = 0;
            lastTime = now;
        }
    }
    
    public String getStatsString() {
        return String.format("FPS: %.1f", currentFps);
    }
}

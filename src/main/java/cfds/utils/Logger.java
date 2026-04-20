package cfds.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    public enum Level { DEBUG, INFO, WARNING, ERROR, CRITICAL }

    private static Logger instance = null;
    private Level currentLevel = Level.INFO;
    private PrintWriter fileWriter = null;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private Logger() {}

    public static synchronized Logger getInstance() {
        if (instance == null) {
            instance = new Logger();
        }
        return instance;
    }

    public synchronized void init(String logDirStr, Level level) {
        this.currentLevel = level;
        try {
            File logDir = new File(logDirStr);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            File logFile = new File(logDir, "cfds_" + dateStr + ".log");
            fileWriter = new PrintWriter(new FileWriter(logFile, true), true);
        } catch (IOException e) {
            System.err.println("Failed to initialize file logger: " + e.getMessage());
        }
    }

    public void log(Level level, String threadName, String message) {
        if (level.ordinal() < currentLevel.ordinal()) return;

        String timestamp = LocalDateTime.now().format(timeFormatter);
        String formatted = String.format("[%s] [%s] [%s] %s", timestamp, level.name(), threadName, message);

        // Print to console with rudimentary ANSI colors
        String colorPrefix = switch (level) {
            case DEBUG -> "\u001B[36m";   // Cyan
            case INFO -> "\u001B[32m";    // Green
            case WARNING -> "\u001B[33m"; // Yellow
            case ERROR, CRITICAL -> "\u001B[31m";  // Red
        };
        System.out.println(colorPrefix + formatted + "\u001B[0m");

        // Print to file
        if (fileWriter != null) {
            synchronized (this) {
                fileWriter.println(formatted);
            }
        }
    }

    public static void info(String message) { getInstance().log(Level.INFO, Thread.currentThread().getName(), message); }
    public static void error(String message) { getInstance().log(Level.ERROR, Thread.currentThread().getName(), message); }
    public static void warning(String message) { getInstance().log(Level.WARNING, Thread.currentThread().getName(), message); }
    public static void debug(String message) { getInstance().log(Level.DEBUG, Thread.currentThread().getName(), message); }
    public static void critical(String message) { getInstance().log(Level.CRITICAL, Thread.currentThread().getName(), message); }
}

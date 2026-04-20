package cfds.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.FileReader;
import java.util.logging.Level;

public class Config {
    private static Config instance = null;
    private JsonObject rootConfig;

    private Config() {}

    public static synchronized Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    public synchronized boolean load(String path) {
        try (FileReader reader = new FileReader(path)) {
            rootConfig = JsonParser.parseReader(reader).getAsJsonObject();
            return true;
        } catch (Exception e) {
            System.err.println("Failed to load config file: " + path);
            return false;
        }
    }

    public String getString(String section, String key, String defaultVal) {
        if (rootConfig != null && rootConfig.has(section) && rootConfig.getAsJsonObject(section).has(key)) {
            return rootConfig.getAsJsonObject(section).get(key).getAsString();
        }
        return defaultVal;
    }

    public int getInt(String section, String key, int defaultVal) {
        if (rootConfig != null && rootConfig.has(section) && rootConfig.getAsJsonObject(section).has(key)) {
            return rootConfig.getAsJsonObject(section).get(key).getAsInt();
        }
        return defaultVal;
    }

    public float getFloat(String section, String key, float defaultVal) {
        if (rootConfig != null && rootConfig.has(section) && rootConfig.getAsJsonObject(section).has(key)) {
            return rootConfig.getAsJsonObject(section).get(key).getAsFloat();
        }
        return defaultVal;
    }

    public boolean getBoolean(String section, String key, boolean defaultVal) {
        if (rootConfig != null && rootConfig.has(section) && rootConfig.getAsJsonObject(section).has(key)) {
            return rootConfig.getAsJsonObject(section).get(key).getAsBoolean();
        }
        return defaultVal;
    }

    public int[] getIntArray(String section, String key, int[] defaultVal) {
        if (rootConfig != null && rootConfig.has(section) && rootConfig.getAsJsonObject(section).has(key)) {
            var array = rootConfig.getAsJsonObject(section).get(key).getAsJsonArray();
            int[] result = new int[array.size()];
            for (int i = 0; i < array.size(); i++) {
                result[i] = array.get(i).getAsInt();
            }
            return result;
        }
        return defaultVal;
    }
}

package cfds.database;

import cfds.utils.Logger;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SQLiteDatabase {
    private Connection connection;
    private String dbPath;

    public SQLiteDatabase() {}

    public boolean connect(String path) {
        this.dbPath = path;
        try {
            File dbFile = new File(path);
            if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }

            // Load SQLite JDBC Driver
            Class.forName("org.sqlite.JDBC");
            
            connection = DriverManager.getConnection("jdbc:sqlite:" + path);
            
            // Apply PRAGMA settings similar to C++ version
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL;");
                stmt.execute("PRAGMA foreign_keys=ON;");
            }

            Logger.info("Connected to SQLite Database: " + path);
            return true;
        } catch (Exception e) {
            Logger.error("Failed to connect to database: " + e.getMessage());
            return false;
        }
    }

    public boolean initializeSchema() {
        if (connection == null) return false;
        String sql = """
            CREATE TABLE IF NOT EXISTS criminals (
                criminal_id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                default_crime TEXT,
                image_path TEXT,
                embedding BLOB NOT NULL
            );
        """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            Logger.info("Database schema initialized.");
            return true;
        } catch (SQLException e) {
            Logger.error("Failed to initialize schema: " + e.getMessage());
            return false;
        }
    }

    private byte[] floatArrayToByteArray(float[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(data.length * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float f : data) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    private float[] byteArrayToFloatArray(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }

    public boolean insertRecord(CriminalRecord rec) {
        if (connection == null) return false;
        String sql = "INSERT OR REPLACE INTO criminals (criminal_id, name, default_crime, image_path, embedding) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, rec.criminal_id);
            pstmt.setString(2, rec.name);
            pstmt.setString(3, rec.default_crime);
            pstmt.setString(4, rec.image_path);
            pstmt.setBytes(5, floatArrayToByteArray(rec.embedding));
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            Logger.error("Failed to insert record: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteRecord(String criminal_id) {
        if (connection == null) return false;
        String sql = "DELETE FROM criminals WHERE criminal_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, criminal_id);
            int rows = pstmt.executeUpdate();
            if (rows > 0) return true;
        } catch (SQLException e) {
            Logger.error("Failed to delete record: " + e.getMessage());
        }
        return false;
    }

    public List<CriminalRecord> getAllRecords() {
        if (connection == null) return new ArrayList<>();
        List<CriminalRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM criminals";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                CriminalRecord rec = new CriminalRecord();
                rec.criminal_id = rs.getString("criminal_id");
                rec.name = rs.getString("name");
                rec.default_crime = rs.getString("default_crime");
                rec.image_path = rs.getString("image_path");
                records.add(rec);
            }
        } catch (SQLException e) {
            Logger.error("Failed to fetch records: " + e.getMessage());
        }
        return records;
    }

    public List<CriminalRecord> fetchAllEmbeddings() {
        if (connection == null) return new ArrayList<>();
        List<CriminalRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM criminals";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                CriminalRecord rec = new CriminalRecord();
                rec.criminal_id = rs.getString("criminal_id");
                rec.name = rs.getString("name");
                rec.default_crime = rs.getString("default_crime");
                rec.image_path = rs.getString("image_path");
                rec.embedding = byteArrayToFloatArray(rs.getBytes("embedding"));
                records.add(rec);
            }
        } catch (SQLException e) {
            Logger.error("Failed to fetch embeddings: " + e.getMessage());
        }
        return records;
    }
}

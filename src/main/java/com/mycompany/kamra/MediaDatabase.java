package com.mycompany.kamra;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
/**
 *
 * @author Kier Tan
 */
public class MediaDatabase {
    public record MediaItem(
            long id,
            String fileName,
            String mediaType,
            LocalDateTime capturedAt,
            String filterName,
            String metadataNote,
            boolean favorite
    ) {}

    private final String jdbcUrl;

    public MediaDatabase(File dbFile) {
        this.jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        initSchema();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void initSchema() {
        String sql = """
                CREATE TABLE IF NOT EXISTS media_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_name TEXT NOT NULL UNIQUE,
                    media_type TEXT NOT NULL,
                    captured_at TEXT NOT NULL,
                    filter_name TEXT NOT NULL,
                    metadata_note TEXT NOT NULL,
                    is_favorite INTEGER NOT NULL DEFAULT 0
                );
                """;
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize media database", e);
        }
    }

    public MediaItem insertMedia(String fileName, String mediaType, LocalDateTime capturedAt, String filterName) {
        String note = "Taken with " + filterName + " filter";
        String isoTime = capturedAt.truncatedTo(ChronoUnit.SECONDS).toString();

        String sql = """
                INSERT OR IGNORE INTO media_items (file_name, media_type, captured_at, filter_name, metadata_note, is_favorite)
                VALUES (?, ?, ?, ?, ?, 0)
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fileName);
            ps.setString(2, mediaType);
            ps.setString(3, isoTime);
            ps.setString(4, filterName);
            ps.setString(5, note);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : -1L;
                return new MediaItem(id, fileName, mediaType, capturedAt, filterName, note, false);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert media record: " + fileName, e);
        }
    }

    public List<MediaItem> getMediaItems(boolean favoritesOnly) {
        String sql = favoritesOnly
                ? "SELECT * FROM media_items WHERE is_favorite = 1 ORDER BY captured_at DESC, id DESC"
                : "SELECT * FROM media_items ORDER BY captured_at DESC, id DESC";

        List<MediaItem> items = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                items.add(new MediaItem(
                        rs.getLong("id"),
                        rs.getString("file_name"),
                        rs.getString("media_type"),
                        LocalDateTime.parse(rs.getString("captured_at")),
                        rs.getString("filter_name"),
                        rs.getString("metadata_note"),
                        rs.getInt("is_favorite") == 1
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query media items", e);
        }
        return items;
    }

    public void setFavorite(String fileName, boolean favorite) {
        String sql = "UPDATE media_items SET is_favorite = ? WHERE file_name = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, favorite ? 1 : 0);
            ps.setString(2, fileName);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update favorite state for " + fileName, e);
        }
    }

    /** Backfills any pre-existing files in kamra_media that aren't in SQLite yet. */
    public void syncExistingFiles(File mediaDir) {
        File[] files = mediaDir.listFiles((dir, name) -> name.endsWith(".png") || name.endsWith(".mp4"));
        if (files == null) return;

        for (File f : files) {
            String type = f.getName().endsWith(".mp4") ? "VIDEO" : "PHOTO";
            LocalDateTime modified = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(f.lastModified()), ZoneId.systemDefault());
            insertMedia(f.getName(), type, modified, "Unknown");
        }
    }
}

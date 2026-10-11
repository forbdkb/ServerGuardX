package com.serverguardx.log;

import com.serverguardx.ServerGuardX;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class GuardLogger {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ServerGuardX plugin;
    private final File logFile;
    private final long maxSizeBytes;
    private final int maxBackups;
    private final boolean enabled;
    private BufferedWriter writer;

    public GuardLogger(ServerGuardX plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("logging.enabled", true);
        this.maxSizeBytes = plugin.getConfig().getLong("logging.max-file-size-mb", 10) * 1024 * 1024;
        this.maxBackups = plugin.getConfig().getInt("logging.max-backups", 5);

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();
        this.logFile = new File(dataFolder, "guardx.log");
        if (enabled) openWriter();
    }

    private void openWriter() {
        try {
            writer = new BufferedWriter(new FileWriter(logFile, true));
        } catch (IOException e) {
            plugin.getLogger().warning("Cannot open guard log: " + e.getMessage());
        }
    }

    public void log(String category, String message) { write("INFO", category, message); }
    public void warn(String category, String message) { write("WARN", category, message); }
    public void error(String message) { write("ERROR", "CORE", message); }

    private synchronized void write(String level, String category, String message) {
        if (!enabled || writer == null) return;
        try {
            rotateIfNeeded();
            String line = String.format("[%s] [%-7s] [%-10s] %s",
                    LocalDateTime.now().format(FMT), level, category, message);
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            plugin.getLogger().warning("Guard log: " + e.getMessage());
        }
    }

    private void rotateIfNeeded() throws IOException {
        if (!logFile.exists() || logFile.length() < maxSizeBytes) return;
        writer.close();
        for (int i = maxBackups - 1; i >= 1; i--) {
            File old = new File(logFile.getParent(), logFile.getName() + "." + i);
            File newer = new File(logFile.getParent(), logFile.getName() + "." + (i + 1));
            if (old.exists()) {
                if (newer.exists()) newer.delete();
                old.renameTo(newer);
            }
        }
        File first = new File(logFile.getParent(), logFile.getName() + ".1");
        if (first.exists()) first.delete();
        logFile.renameTo(first);
        openWriter();
    }

    public File getFile() { return logFile; }

    public synchronized void close() {
        try { if (writer != null) writer.close(); }
        catch (IOException ignored) {}
    }
}

package com.serverguardx.analyze;

import com.serverguardx.ServerGuardX;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BaselineTracker {

    private final ServerGuardX plugin;
    private final File file;
    private FileConfiguration data;

    private double baselineMspt = 0;
    private double baselinePlayers = 0;
    private double baselineEntities = 0;
    private int samplesLearned = 0;

    public BaselineTracker(ServerGuardX plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "baseline.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(file);
        baselineMspt = data.getDouble("baseline.mspt", 0);
        baselinePlayers = data.getDouble("baseline.players", 0);
        baselineEntities = data.getDouble("baseline.entities", 0);
        samplesLearned = data.getInt("baseline.samples", 0);
    }

    public void learn(double mspt, int players, int entities) {
        if (mspt > 80) return;
        if (!plugin.getConfig().getBoolean("analysis.baseline-enabled", true)) return;

        samplesLearned++;
        double alpha = Math.max(0.001, 1.0 / samplesLearned);
        baselineMspt = baselineMspt == 0 ? mspt : baselineMspt * (1 - alpha) + mspt * alpha;
        baselinePlayers = baselinePlayers == 0 ? players : baselinePlayers * (1 - alpha) + players * alpha;
        baselineEntities = baselineEntities == 0 ? entities : baselineEntities * (1 - alpha) + entities * alpha;

        if (samplesLearned % 100 == 0) save();
    }

    public Deviation checkDeviation(double currentMspt, int currentPlayers, int currentEntities) {
        Deviation d = new Deviation();
        int minSamples = plugin.getConfig().getInt("analysis.baseline-min-samples", 50);

        if (samplesLearned < minSamples) {
            d.learned = false;
            d.message = "Learning (" + samplesLearned + "/" + minSamples + ")";
            return d;
        }

        d.learned = true;
        d.baselineMspt = baselineMspt;
        d.currentMspt = currentMspt;
        d.msptDeviation = baselineMspt > 0
                ? ((currentMspt - baselineMspt) / baselineMspt * 100) : 0;

        if (Math.abs(d.msptDeviation) > 200) {
            d.severity = "CRITICAL";
            d.message = String.format("MSPT %.0f%% above baseline", d.msptDeviation);
        } else if (Math.abs(d.msptDeviation) > 100) {
            d.severity = "WARNING";
            d.message = String.format("MSPT %.0f%% above baseline", d.msptDeviation);
        } else if (Math.abs(d.msptDeviation) > 50) {
            d.severity = "INFO";
            d.message = String.format("%.0f%% deviation", d.msptDeviation);
        } else {
            d.severity = "NORMAL";
            d.message = "Within normal range";
        }

        if (baselinePlayers > 0 && currentPlayers > baselinePlayers * 2)
            d.notes.add("Players doubled");
        if (baselineEntities > 0 && currentEntities > baselineEntities * 1.5)
            d.notes.add("Entities 50%+ above baseline");

        return d;
    }

    private void save() {
        try {
            data.set("baseline.mspt", baselineMspt);
            data.set("baseline.players", baselinePlayers);
            data.set("baseline.entities", baselineEntities);
            data.set("baseline.samples", samplesLearned);
            data.save(file);
        } catch (IOException e) {
            plugin.logs().error("Baseline save: " + e.getMessage());
        }
    }

    public int getSamplesLearned() { return samplesLearned; }
    public double getBaselineMspt() { return baselineMspt; }
    public double getBaselinePlayers() { return baselinePlayers; }
    public double getBaselineEntities() { return baselineEntities; }

    public static class Deviation {
        public boolean learned;
        public String severity = "UNKNOWN";
        public String message = "";
        public double baselineMspt, currentMspt, msptDeviation;
        public List<String> notes = new ArrayList<>();
    }
}

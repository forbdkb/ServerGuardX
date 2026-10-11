package com.serverguardx.analyze;

import com.serverguardx.ServerGuardX;
import com.serverguardx.core.Sampler;
import com.serverguardx.core.TickProfiler;
import org.bukkit.Bukkit;

import java.util.*;

public class LagAnalyzer {

    private final ServerGuardX plugin;

    public LagAnalyzer(ServerGuardX plugin) { this.plugin = plugin; }

    public void quickScan() {
        Sampler.Snapshot snap = plugin.samples().getLatest();
        if (snap == null) return;

        List<String> causes = detectRootCauses(snap);
        if (causes.isEmpty()) {
            plugin.logs().log("ANALYSIS", "No root cause detected");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n§8§m                                                        \n");
        sb.append("§c⚡ §lLAG ROOT CAUSE ANALYSIS §r§c⚡\n");
        sb.append(String.format("§7MSPT: §f%.2fms\n", snap.mspt));
        sb.append("§7Possible causes:\n");
        for (String cause : causes) sb.append("§7  • ").append(cause).append("\n");
        sb.append("§8§m                                                        ");

        Bukkit.getConsoleSender().sendMessage(sb.toString());
        plugin.logs().log("ANALYSIS", String.join(" | ", causes));
        plugin.discord().send("🔍 **Analysis**: " + String.join("\n• ", causes));
    }

    public AnalysisReport deepScan() {
        AnalysisReport report = new AnalysisReport();
        report.timestamp = System.currentTimeMillis();

        TickProfiler tp = plugin.ticks();
        report.currentMspt = tp.getLastMspt();
        report.averageMspt = tp.getAverageMspt();
        report.p95Mspt = tp.getP95Mspt();
        report.tps = Math.min(20.0, 1000.0 / Math.max(1, report.averageMspt));

        Sampler.Snapshot snap = plugin.samples().getLatest();
        report.snapshot = snap;

        if (snap != null) {
            report.ramPercent = snap.heapMax > 0
                    ? (int) (snap.heapUsed * 100 / snap.heapMax) : 0;
            report.topEntities = snap.topEntities;
            report.totalEntities = snap.totalEntities;
            report.totalChunks = snap.totalChunks;
        }

        report.slowestTicks = tp.getSlowestTicks(10);
        report.detectedCauses = detectRootCauses(snap);
        report.recommendations = buildRecommendations(report);

        plugin.logs().log("REPORT", formatReportForLog(report));
        return report;
    }

    private List<String> detectRootCauses(Sampler.Snapshot snap) {
        List<String> causes = new ArrayList<>();
        if (snap == null) return causes;

        int ramPct = snap.heapMax > 0 ? (int) (snap.heapUsed * 100 / snap.heapMax) : 0;
        if (ramPct >= 95) causes.add("§cRAM critically high (" + ramPct + "%) — GC eating CPU");
        else if (ramPct >= 85) causes.add("§eRAM high (" + ramPct + "%)");

        if (snap.totalEntities > 5000) causes.add("§c" + snap.totalEntities + " entities — tick lag");
        else if (snap.totalEntities > 3000) causes.add("§e" + snap.totalEntities + " entities");

        if (snap.topEntities != null && !snap.topEntities.isEmpty()) {
            var top = snap.topEntities.get(0);
            if (top.getValue() > 500) {
                String type = top.getKey();
                if (type.contains("ITEM")) causes.add("§c" + top.getValue() + " dropped items");
                else if (type.contains("VILLAGER")) causes.add("§c" + top.getValue() + " villagers");
                else causes.add("§e" + top.getValue() + " × " + type);
            }
        }

        if (snap.totalChunks > 20000) causes.add("§e" + snap.totalChunks + " chunks loaded");
        if (snap.maxPing > 500) causes.add("§cHigh ping: " + snap.maxPing + "ms");
        if (snap.mspt > 100 && snap.playerCount < 10) causes.add("§cLag with few players");
        return causes;
    }

    private List<String> buildRecommendations(AnalysisReport r) {
        List<String> recs = new ArrayList<>();
        if (r.ramPercent >= 85) recs.add("§7Allocate more RAM");
        if (r.totalEntities > 3000) recs.add("§7Run §f/sg entities");
        if (r.p95Mspt > 100) recs.add("§7Check §f/sg plugins");
        return recs;
    }

    private String formatReportForLog(AnalysisReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n════════ ANALYSIS REPORT ════════\n");
        sb.append(String.format("MSPT: %.2f | P95: %.2f | TPS: %.2f%n",
                r.currentMspt, r.p95Mspt, r.tps));
        sb.append(String.format("RAM: %d%% | Entities: %d | Chunks: %d%n",
                r.ramPercent, r.totalEntities, r.totalChunks));
        sb.append("Causes:\n");
        for (String c : r.detectedCauses) sb.append("  - ").append(c).append("\n");
        sb.append("════════════════════════════════\n");
        return sb.toString();
    }

    public static class AnalysisReport {
        public long timestamp;
        public double currentMspt, averageMspt, p95Mspt, tps;
        public int ramPercent, totalEntities, totalChunks;
        public List<Map.Entry<String, Integer>> topEntities = Collections.emptyList();
        public List<TickProfiler.TickData> slowestTicks = Collections.emptyList();
        public List<String> detectedCauses = new ArrayList<>();
        public List<String> recommendations = new ArrayList<>();
        public Sampler.Snapshot snapshot;
    }
}

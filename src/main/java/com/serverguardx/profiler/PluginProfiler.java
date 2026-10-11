package com.serverguardx.profiler;

import com.serverguardx.ServerGuardX;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PluginProfiler {

    private final ServerGuardX plugin;
    private final Map<String, PluginMetrics> metrics = new ConcurrentHashMap<>();
    private BukkitTask task;

    public PluginProfiler(ServerGuardX plugin) { this.plugin = plugin; }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                this::snapshot, 1200L, 1200L);
    }

    public void stop() { if (task != null) task.cancel(); }

    public void recordPluginTick(String name, long nanos) {
        PluginMetrics m = metrics.computeIfAbsent(name, k -> new PluginMetrics(k));
        m.record(nanos);
    }

    private void snapshot() {
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            PluginMetrics m = metrics.computeIfAbsent(p.getName(), k -> new PluginMetrics(k));
            m.computeStats();
        }
    }

    public List<PluginReport> getReports() {
        List<PluginReport> reports = new ArrayList<>();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            PluginMetrics m = metrics.get(p.getName());
            PluginReport r = new PluginReport();
            r.name = p.getName();
            r.version = p.getDescription().getVersion();
            r.enabled = p.isEnabled();
            if (m != null) {
                r.avgMspt = m.avgMs;
                r.maxMspt = m.maxMs;
                r.p95Mspt = m.p95Ms;
                r.totalCalls = m.totalCalls;
                r.samples = m.samples.size();
            }
            reports.add(r);
        }
        reports.sort((a, b) -> Double.compare(b.avgMspt, a.avgMspt));
        return reports;
    }

    public PluginReport getTopOffender() {
        List<PluginReport> r = getReports();
        return r.isEmpty() ? null : r.get(0);
    }

    public double getTotalPluginMspt() {
        double sum = 0;
        for (PluginMetrics m : metrics.values()) sum += m.avgMs;
        return sum;
    }

    private static class PluginMetrics {
        final String name;
        final Deque<Long> samples = new ArrayDeque<>(600);
        double avgMs = 0, maxMs = 0, p95Ms = 0;
        long totalCalls = 0;

        PluginMetrics(String name) { this.name = name; }

        synchronized void record(long nanos) {
            double ms = nanos / 1_000_000.0;
            samples.addLast((long) ms);
            if (samples.size() > 600) samples.pollFirst();
            totalCalls++;
        }

        synchronized void computeStats() {
            if (samples.isEmpty()) { avgMs = maxMs = p95Ms = 0; return; }
            List<Long> sorted = new ArrayList<>(samples);
            sorted.sort(Long::compare);
            long sum = 0;
            for (long v : sorted) sum += v;
            avgMs = (double) sum / sorted.size();
            maxMs = sorted.get(sorted.size() - 1);
            int idx = (int) Math.ceil(sorted.size() * 0.95) - 1;
            p95Ms = sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
        }
    }

    public static class PluginReport {
        public String name, version;
        public boolean enabled;
        public double avgMspt, maxMspt, p95Mspt;
        public long totalCalls;
        public int samples;
    }
}

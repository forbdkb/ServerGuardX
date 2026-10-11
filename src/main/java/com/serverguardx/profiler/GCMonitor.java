package com.serverguardx.profiler;

import com.serverguardx.ServerGuardX;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

public class GCMonitor {

    private final ServerGuardX plugin;
    private final Deque<GCEvent> recentEvents = new ConcurrentLinkedDeque<>();
    private final List<GarbageCollectorMXBean> gcBeans;
    private BukkitTask task;
    private String gcType = "Unknown";
    private long lastGCCount = 0, lastGCTime = 0;

    public GCMonitor(ServerGuardX plugin) {
        this.plugin = plugin;
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        detectGCType();
    }

    private void detectGCType() {
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.contains("UseG1GC")) gcType = "G1GC";
            else if (arg.contains("UseZGC")) gcType = "ZGC";
            else if (arg.contains("UseShenandoahGC")) gcType = "Shenandoah";
            else if (arg.contains("UseParallelGC")) gcType = "ParallelGC";
            else if (arg.contains("UseSerialGC")) gcType = "SerialGC";
        }
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::check, 100L, 100L);
    }

    public void stop() { if (task != null) task.cancel(); }

    private void check() {
        try {
            long count = 0, time = 0;
            for (GarbageCollectorMXBean gc : gcBeans) {
                long c = gc.getCollectionCount();
                long t = gc.getCollectionTime();
                if (c > 0) { count += c; time += t; }
            }

            if (count > lastGCCount) {
                long gcCountDelta = count - lastGCCount;
                long gcTimeDelta = time - lastGCTime;

                if (gcTimeDelta > 0) {
                    GCEvent ev = new GCEvent();
                    ev.timestamp = System.currentTimeMillis();
                    ev.pauseMs = gcTimeDelta;
                    ev.count = gcCountDelta;
                    recentEvents.addLast(ev);
                    while (recentEvents.size() > 100) recentEvents.pollFirst();

                    if (gcTimeDelta > 50) {
                        plugin.logs().warn("GC", "Long pause: " + gcTimeDelta + "ms");
                        if (gcTimeDelta > 200) {
                            plugin.discord().send("🔴 **GC PAUSE** `" + gcTimeDelta + "ms`");
                        }
                    }
                }
                lastGCCount = count;
                lastGCTime = time;
            }
        } catch (Throwable t) {
            plugin.logs().error("GCMonitor: " + t.getMessage());
        }
    }

    public GCStats getStats() {
        GCStats s = new GCStats();
        s.gcType = gcType;

        for (GarbageCollectorMXBean gc : gcBeans) {
            s.totalCount += gc.getCollectionCount();
            s.totalTimeMs += gc.getCollectionTime();
        }

        long cutoff = System.currentTimeMillis() - 60_000;
        long pauseSum = 0, pauseMax = 0;
        int events = 0;
        for (GCEvent e : recentEvents) {
            if (e.timestamp >= cutoff) {
                pauseSum += e.pauseMs;
                pauseMax = Math.max(pauseMax, e.pauseMs);
                events++;
            }
        }

        s.pausesLastMinute = events;
        s.avgPauseMs = events > 0 ? (double) pauseSum / events : 0;
        s.maxPauseMs = pauseMax;

        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        s.gcPerMinute = uptime > 0 ? (double) s.totalCount * 60_000 / uptime : 0;

        if (s.maxPauseMs > 500 || s.gcPerMinute > 30) s.health = "CRITICAL";
        else if (s.maxPauseMs > 100 || s.gcPerMinute > 10) s.health = "WARNING";
        else s.health = "GOOD";

        return s;
    }

    private static class GCEvent {
        long timestamp, pauseMs, count;
    }

    public static class GCStats {
        public String gcType = "Unknown";
        public String health = "GOOD";
        public long totalCount;
        public long totalTimeMs;
        public int pausesLastMinute;
        public double avgPauseMs;
        public long maxPauseMs;
        public double gcPerMinute;
    }
}

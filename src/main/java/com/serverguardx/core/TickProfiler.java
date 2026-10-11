package com.serverguardx.core;

import com.serverguardx.ServerGuardX;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class TickProfiler {

    private final ServerGuardX plugin;
    private final Deque<TickData> tickHistory;
    private final int historySize;
    private final Deque<Double> msptWindow = new ArrayDeque<>();

    private long tickStart;
    private long tickNumber = 0;
    private boolean running = false;
    private double lastMspt = 0;
    private int consecutiveLagTicks = 0;
    private long lastAlertTime = 0;

    public TickProfiler(ServerGuardX plugin) {
        this.plugin = plugin;
        this.historySize = plugin.getConfig().getInt("monitor.tick-history-size", 1200);
        this.tickHistory = new ArrayDeque<>(historySize);
    }

    public void start() {
        if (running) return;
        running = true;
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.nanoTime();
                if (tickStart != 0) {
                    double mspt = (now - tickStart) / 1_000_000.0;
                    recordTick(mspt);
                }
                tickStart = now;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void recordTick(double mspt) {
        tickNumber++;
        lastMspt = mspt;

        TickData data = new TickData(tickNumber, mspt, System.currentTimeMillis());
        synchronized (tickHistory) {
            if (tickHistory.size() >= historySize) tickHistory.pollFirst();
            tickHistory.addLast(data);
        }

        msptWindow.addLast(mspt);
        if (msptWindow.size() > 200) msptWindow.pollFirst();

        double warn = plugin.getConfig().getDouble("alerts.mspt-warning", 60.0);
        double crit = plugin.getConfig().getDouble("alerts.mspt-critical", 100.0);
        int need = plugin.getConfig().getInt("alerts.consecutive-ticks-before-alert", 40);
        long cooldown = plugin.getConfig().getLong("alerts.alert-cooldown-seconds", 120) * 1000L;

        if (mspt >= warn) {
            consecutiveLagTicks++;
            long nowMs = System.currentTimeMillis();
            if (consecutiveLagTicks >= need && nowMs - lastAlertTime > cooldown) {
                lastAlertTime = nowMs;
                onLagDetected(mspt, mspt >= crit);
                consecutiveLagTicks = 0;
            }
        } else {
            consecutiveLagTicks = 0;
        }
    }

    private void onLagDetected(double mspt, boolean critical) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String level = critical ? "CRITICAL" : "WARNING";
            plugin.logs().warn("LAG", String.format("%s spike: %.2f MSPT", level, mspt));
            String msg = String.format("⚠️ **%s LAG** — `%.2f MSPT`", level, mspt);
            plugin.discord().send(msg);
            if (critical) plugin.analyzer().quickScan();
        });
    }

    public void stop() { running = false; }

    public double getLastMspt() { return lastMspt; }

    public double getAverageMspt() {
        synchronized (msptWindow) {
            if (msptWindow.isEmpty()) return 0;
            double sum = 0;
            for (double v : msptWindow) sum += v;
            return sum / msptWindow.size();
        }
    }

    public double getP95Mspt() {
        List<Double> copy;
        synchronized (msptWindow) { copy = new ArrayList<>(msptWindow); }
        if (copy.isEmpty()) return 0;
        copy.sort(Double::compare);
        int idx = (int) Math.ceil(copy.size() * 0.95) - 1;
        return copy.get(Math.max(0, Math.min(idx, copy.size() - 1)));
    }

    public List<TickData> getHistorySnapshot() {
        synchronized (tickHistory) { return new ArrayList<>(tickHistory); }
    }

    public List<TickData> getSlowestTicks(int count) {
        List<TickData> copy;
        synchronized (tickHistory) { copy = new ArrayList<>(tickHistory); }
        copy.sort((a, b) -> Double.compare(b.mspt, a.mspt));
        return copy.subList(0, Math.min(count, copy.size()));
    }

    public static class TickData {
        public final long tick;
        public final double mspt;
        public final long timestamp;
        public TickData(long tick, double mspt, long timestamp) {
            this.tick = tick; this.mspt = mspt; this.timestamp = timestamp;
        }
    }
}

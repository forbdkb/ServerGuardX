package com.serverguardx.core;

import com.serverguardx.ServerGuardX;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Sampler {

    private final ServerGuardX plugin;
    private BukkitRunnable task;
    private volatile Snapshot latest = new Snapshot();
    private final Deque<Snapshot> history = new ArrayDeque<>(60);

    public Sampler(ServerGuardX plugin) { this.plugin = plugin; }

    public void start() {
        if (!plugin.getConfig().getBoolean("monitor.enabled", true)) return;
        int intervalSec = plugin.getConfig().getInt("monitor.sample-interval-seconds", 30);
        long intervalTicks = intervalSec * 20L;

        task = new BukkitRunnable() {
            @Override
            public void run() {
                try { collectSnapshot(); }
                catch (Throwable t) { plugin.logs().error("Sampler: " + t.getMessage()); }
            }
        };
        task.runTaskTimer(plugin, 100L, intervalTicks);
    }

    public void stop() { if (task != null) task.cancel(); }

    private void collectSnapshot() {
        Snapshot snap = new Snapshot();
        snap.timestamp = System.currentTimeMillis();
        snap.mspt = plugin.ticks().getLastMspt();
        snap.avgMspt = plugin.ticks().getAverageMspt();

        Runtime rt = Runtime.getRuntime();
        snap.heapUsed = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        snap.heapMax = rt.maxMemory() / 1024 / 1024;

        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        snap.playerCount = players.size();
        long pingSum = 0; int pingMax = 0;
        for (Player p : players) {
            int ping = p.getPing();
            pingSum += ping;
            if (ping > pingMax) pingMax = ping;
        }
        snap.avgPing = players.isEmpty() ? 0 : (int) (pingSum / players.size());
        snap.maxPing = pingMax;

        Map<String, Integer> entityCounts = new HashMap<>();
        int totalEntities = 0, totalChunks = 0;
        for (World world : Bukkit.getWorlds()) {
            totalChunks += world.getLoadedChunks().length;
            try {
                Collection<Entity> entities = world.getEntities();
                totalEntities += entities.size();
                for (Entity e : entities) {
                    entityCounts.merge(e.getType().name(), 1, Integer::sum);
                }
            } catch (Throwable ignored) {}
        }

        snap.totalEntities = totalEntities;
        snap.totalChunks = totalChunks;
        snap.entityCounts = entityCounts;

        List<Map.Entry<String, Integer>> top = new ArrayList<>(entityCounts.entrySet());
        top.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        snap.topEntities = top.size() > 10 ? top.subList(0, 10) : top;

        this.latest = snap;
        synchronized (history) {
            if (history.size() >= 60) history.pollFirst();
            history.addLast(snap);
        }

        if (plugin.baseline() != null) {
            plugin.baseline().learn(snap.mspt, snap.playerCount, snap.totalEntities);
        }
    }

    public Snapshot getLatest() { return latest; }

    public List<Snapshot> getHistory() {
        synchronized (history) { return new ArrayList<>(history); }
    }

    public static class Snapshot {
        public long timestamp;
        public double mspt;
        public double avgMspt;
        public long heapUsed;
        public long heapMax;
        public int playerCount;
        public int avgPing;
        public int maxPing;
        public int totalEntities;
        public int totalChunks;
        public Map<String, Integer> entityCounts = Collections.emptyMap();
        public List<Map.Entry<String, Integer>> topEntities = Collections.emptyList();
    }
}

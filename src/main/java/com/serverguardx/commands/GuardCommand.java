package com.serverguardx.commands;

import com.serverguardx.ServerGuardX;
import com.serverguardx.analyze.BaselineTracker;
import com.serverguardx.analyze.LagAnalyzer;
import com.serverguardx.core.Sampler;
import com.serverguardx.core.TickProfiler;
import com.serverguardx.profiler.GCMonitor;
import com.serverguardx.profiler.PluginProfiler;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class GuardCommand implements CommandExecutor, TabCompleter {

    private final ServerGuardX plugin;

    public GuardCommand(ServerGuardX plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!s.hasPermission("serverguardx.use") && !s.isOp()) {
            s.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 0) { help(s); return true; }

        try {
            switch (args[0].toLowerCase()) {
                case "help" -> help(s);
                case "analyze", "scan" -> analyze(s);
                case "stats", "info" -> stats(s);
                case "ticks" -> ticks(s);
                case "entities" -> entities(s);
                case "memory", "ram" -> memory(s);
                case "players" -> players(s);
                case "plugins", "profiler" -> plugins(s);
                case "gc" -> gc(s);
                case "baseline" -> baseline(s);
                case "log" -> log(s);
                default -> help(s);
            }
        } catch (Throwable t) {
            s.sendMessage(ChatColor.RED + "Error: " + t.getMessage());
            plugin.logs().error("Command error: " + t.getMessage());
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "╔══════ ServerGuardX ══════╗");
        s.sendMessage(ChatColor.YELLOW + "/sg analyze " + ChatColor.GRAY + "- Full diagnosis");
        s.sendMessage(ChatColor.YELLOW + "/sg stats " + ChatColor.GRAY + "- Quick stats");
        s.sendMessage(ChatColor.YELLOW + "/sg plugins " + ChatColor.GRAY + "- Plugin list");
        s.sendMessage(ChatColor.YELLOW + "/sg gc " + ChatColor.GRAY + "- Garbage Collector");
        s.sendMessage(ChatColor.YELLOW + "/sg baseline " + ChatColor.GRAY + "- Compare baseline");
        s.sendMessage(ChatColor.YELLOW + "/sg ticks " + ChatColor.GRAY + "- Slowest ticks");
        s.sendMessage(ChatColor.YELLOW + "/sg entities " + ChatColor.GRAY + "- Top entities");
        s.sendMessage(ChatColor.YELLOW + "/sg memory " + ChatColor.GRAY + "- RAM usage");
        s.sendMessage(ChatColor.YELLOW + "/sg players " + ChatColor.GRAY + "- Player pings");
        s.sendMessage(ChatColor.YELLOW + "/sg log " + ChatColor.GRAY + "- Log file");
        s.sendMessage(ChatColor.GOLD + "╚══════════════════════════╝");
    }

    private void analyze(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "⏳ Running analysis...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                LagAnalyzer.AnalysisReport r = plugin.analyzer().deepScan();

                s.sendMessage(ChatColor.GOLD + "╔══════ ANALYSIS ══════╗");
                s.sendMessage(ChatColor.YELLOW + "MSPT: " + ChatColor.WHITE
                        + String.format("%.2f", r.currentMspt)
                        + ChatColor.YELLOW + " | P95: " + ChatColor.WHITE
                        + String.format("%.2f", r.p95Mspt)
                        + ChatColor.YELLOW + " | TPS: " + ChatColor.WHITE
                        + String.format("%.2f", r.tps));

                if (r.snapshot != null) {
                    s.sendMessage(ChatColor.YELLOW + "Players: " + ChatColor.WHITE
                            + r.snapshot.playerCount
                            + ChatColor.YELLOW + " | RAM: " + ChatColor.WHITE
                            + r.ramPercent + "%"
                            + ChatColor.YELLOW + " | Ent: " + ChatColor.WHITE
                            + r.totalEntities);
                }

                s.sendMessage("");
                s.sendMessage(ChatColor.RED + "▸ Causes:");
                if (r.detectedCauses.isEmpty()) {
                    s.sendMessage(ChatColor.GREEN + "  ✓ No problems");
                } else {
                    for (String c : r.detectedCauses) {
                        s.sendMessage("  " + ChatColor.translateAlternateColorCodes('&', c));
                    }
                }

                if (!r.recommendations.isEmpty()) {
                    s.sendMessage("");
                    s.sendMessage(ChatColor.AQUA + "▸ Recommendations:");
                    for (String rec : r.recommendations) {
                        s.sendMessage("  " + ChatColor.translateAlternateColorCodes('&', rec));
                    }
                }
                s.sendMessage(ChatColor.GOLD + "╚══════════════════════╝");
            } catch (Throwable t) {
                s.sendMessage(ChatColor.RED + "Analysis error: " + t.getMessage());
            }
        });
    }

    private void stats(CommandSender s) {
        TickProfiler tp = plugin.ticks();
        Sampler.Snapshot snap = plugin.samples().getLatest();

        s.sendMessage(ChatColor.GOLD + "╔══ Quick Stats ══╗");
        s.sendMessage(ChatColor.YELLOW + "MSPT: " + ChatColor.WHITE
                + String.format("%.2f", tp.getLastMspt())
                + ChatColor.YELLOW + " | P95: " + ChatColor.WHITE
                + String.format("%.2f", tp.getP95Mspt()));

        if (snap != null) {
            s.sendMessage(ChatColor.YELLOW + "Players: " + ChatColor.WHITE
                    + snap.playerCount
                    + ChatColor.GRAY + " (ping " + snap.avgPing + "ms)");

            s.sendMessage(ChatColor.YELLOW + "RAM: " + ChatColor.WHITE
                    + snap.heapUsed + "/" + snap.heapMax + " MB");

            s.sendMessage(ChatColor.YELLOW + "Entities: " + ChatColor.WHITE
                    + snap.totalEntities
                    + ChatColor.YELLOW + " | Chunks: " + ChatColor.WHITE
                    + snap.totalChunks);
        }
        s.sendMessage(ChatColor.GOLD + "╚═════════════════╝");
    }

    private void ticks(CommandSender s) {
        List<TickProfiler.TickData> slow = plugin.ticks().getSlowestTicks(10);
        s.sendMessage(ChatColor.GOLD + "╔══ Slowest Ticks ══╗");
        for (int i = 0; i < slow.size(); i++) {
            TickProfiler.TickData t = slow.get(i);
            ChatColor c = t.mspt > 100 ? ChatColor.RED :
                          t.mspt > 60 ? ChatColor.YELLOW : ChatColor.GREEN;
            s.sendMessage(ChatColor.GRAY + "#" + (i + 1) + " " + c
                    + String.format("%.2f", t.mspt) + "ms "
                    + ChatColor.DARK_GRAY + "(tick " + t.tick + ")");
        }
        s.sendMessage(ChatColor.GOLD + "╚══════════════════╝");
    }

    private void entities(CommandSender s) {
        Sampler.Snapshot snap = plugin.samples().getLatest();
        if (snap == null || snap.topEntities.isEmpty()) {
            s.sendMessage(ChatColor.YELLOW + "No data yet.");
            return;
        }
        s.sendMessage(ChatColor.GOLD + "╔══ Top Entities ══╗");
        s.sendMessage(ChatColor.YELLOW + "Total: " + ChatColor.WHITE + snap.totalEntities);
        for (Map.Entry<String, Integer> e : snap.topEntities) {
            ChatColor c = e.getValue() > 500 ? ChatColor.RED :
                          e.getValue() > 200 ? ChatColor.YELLOW : ChatColor.GREEN;
            s.sendMessage(ChatColor.GRAY + "  " + e.getKey() + " " + c + e.getValue());
        }
        s.sendMessage(ChatColor.GOLD + "╚═════════════════╝");
    }

    private void memory(CommandSender s) {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long max = rt.maxMemory() / 1024 / 1024;
        int pct = max > 0 ? (int) (used * 100 / max) : 0;

        s.sendMessage(ChatColor.GOLD + "╔══ Memory ══╗");
        s.sendMessage(ChatColor.YELLOW + "Used: " + ChatColor.WHITE + used + " MB"
                + ChatColor.YELLOW + " | Max: " + ChatColor.WHITE + max + " MB");
        ChatColor c = pct > 90 ? ChatColor.RED : pct > 75 ? ChatColor.YELLOW : ChatColor.GREEN;
        s.sendMessage(ChatColor.YELLOW + "Usage: " + c + pct + "%");
        s.sendMessage(ChatColor.GOLD + "╚════════════╝");
    }

    private void players(CommandSender s) {
        var players = plugin.getServer().getOnlinePlayers();
        s.sendMessage(ChatColor.GOLD + "╔══ Players (" + players.size() + ") ══╗");
        for (var p : players) {
            int ping = p.getPing();
            ChatColor c = ping < 80 ? ChatColor.GREEN :
                          ping < 150 ? ChatColor.YELLOW : ChatColor.RED;
            s.sendMessage(ChatColor.GRAY + "  " + p.getName() + " " + c + ping + "ms");
        }
        s.sendMessage(ChatColor.GOLD + "╚══════════════════╝");
    }

    private void plugins(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "╔══ Plugin List ══╗");
        s.sendMessage(ChatColor.YELLOW + "Enabled: " + ChatColor.WHITE
                + plugin.profiler().getEnabledPluginCount());

        List<PluginProfiler.PluginReport> reports = plugin.profiler().getReports();
        int shown = 0;
        for (PluginProfiler.PluginReport r : reports) {
            if (shown++ >= 20) break;
            ChatColor state = r.enabled ? ChatColor.GREEN : ChatColor.RED;
            String taskInfo = r.activeTasks >= 0 ? " (" + r.activeTasks + " tasks)" : "";
            s.sendMessage(state + (r.enabled ? "✓ " : "✗ ")
                    + ChatColor.WHITE + r.name
                    + ChatColor.DARK_GRAY + " v" + r.version
                    + ChatColor.GRAY + taskInfo);
        }
        s.sendMessage(ChatColor.GOLD + "╚═════════════════╝");
    }

    private void gc(CommandSender s) {
        GCMonitor.GCStats stats = plugin.gc().getStats();
        ChatColor hc;
        switch (stats.health) {
            case "CRITICAL": hc = ChatColor.RED; break;
            case "WARNING": hc = ChatColor.YELLOW; break;
            default: hc = ChatColor.GREEN;
        }

        s.sendMessage(ChatColor.GOLD + "╔══ Garbage Collector ══╗");
        s.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + stats.gcType);
        s.sendMessage(ChatColor.YELLOW + "Health: " + hc + stats.health);
        s.sendMessage(ChatColor.YELLOW + "Rate: " + ChatColor.WHITE
                + String.format("%.1f", stats.gcPerMinute) + "/min");
        s.sendMessage(ChatColor.YELLOW + "Pauses: " + ChatColor.WHITE + stats.pausesLastMinute);
        s.sendMessage(ChatColor.YELLOW + "Avg: " + ChatColor.WHITE
                + String.format("%.1f", stats.avgPauseMs) + "ms");
        s.sendMessage(ChatColor.YELLOW + "Max: " + ChatColor.WHITE + stats.maxPauseMs + "ms");
        s.sendMessage(ChatColor.GOLD + "╚═══════════════════════╝");
    }

    private void baseline(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "╔══ Baseline ══╗");
        s.sendMessage(ChatColor.YELLOW + "Samples: " + ChatColor.WHITE
                + plugin.baseline().getSamplesLearned());
        s.sendMessage(ChatColor.YELLOW + "Baseline: " + ChatColor.WHITE
                + String.format("%.2f", plugin.baseline().getBaselineMspt()) + " MSPT");

        Sampler.Snapshot snap = plugin.samples().getLatest();
        if (snap != null) {
            BaselineTracker.Deviation dev = plugin.baseline().checkDeviation(
                    snap.mspt, snap.playerCount, snap.totalEntities);
            ChatColor dc;
            switch (dev.severity) {
                case "CRITICAL": dc = ChatColor.RED; break;
                case "WARNING": dc = ChatColor.YELLOW; break;
                case "NORMAL": dc = ChatColor.GREEN; break;
                default: dc = ChatColor.GRAY;
            }
            s.sendMessage(ChatColor.YELLOW + "Status: " + dc + dev.message);
            for (String note : dev.notes) {
                s.sendMessage(ChatColor.GRAY + "  • " + note);
            }
        }
        s.sendMessage(ChatColor.GOLD + "╚═════════════╝");
    }

    private void log(CommandSender s) {
        s.sendMessage(ChatColor.YELLOW + "Log: " + ChatColor.WHITE
                + plugin.logs().getFile().getAbsolutePath());
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("analyze", "stats", "plugins", "gc",
                    "baseline", "ticks", "entities", "memory",
                    "players", "log", "help");
        }
        return new ArrayList<>();
    }
}

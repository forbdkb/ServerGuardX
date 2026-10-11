package com.serverguardx;

import com.serverguardx.analyze.BaselineTracker;
import com.serverguardx.analyze.LagAnalyzer;
import com.serverguardx.commands.GuardCommand;
import com.serverguardx.core.Sampler;
import com.serverguardx.core.TickProfiler;
import com.serverguardx.log.GuardLogger;
import com.serverguardx.notify.DiscordNotifier;
import com.serverguardx.profiler.GCMonitor;
import com.serverguardx.profiler.PluginProfiler;
import com.serverguardx.web.WebDashboard;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerGuardX extends JavaPlugin {

    private static ServerGuardX instance;

    private GuardLogger guardLogger;
    private DiscordNotifier discord;
    private TickProfiler tickProfiler;
    private Sampler sampler;
    private LagAnalyzer analyzer;
    private BaselineTracker baseline;
    private PluginProfiler pluginProfiler;
    private GCMonitor gcMonitor;
    private WebDashboard webDashboard;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        printBanner();

        this.guardLogger = new GuardLogger(this);
        this.discord = new DiscordNotifier(this);
        this.tickProfiler = new TickProfiler(this);
        this.sampler = new Sampler(this);
        this.analyzer = new LagAnalyzer(this);
        this.baseline = new BaselineTracker(this);
        this.pluginProfiler = new PluginProfiler(this);
        this.gcMonitor = new GCMonitor(this);

        tickProfiler.start();
        sampler.start();
        pluginProfiler.start();
        gcMonitor.start();

        if (getConfig().getBoolean("web.enabled", true)) {
            try {
                this.webDashboard = new WebDashboard(this);
                webDashboard.start();
            } catch (Throwable e) {
                getLogger().warning("Web dashboard failed: " + e.getMessage());
            }
        }

        var cmd = getCommand("sg");
        if (cmd != null) {
            GuardCommand gc = new GuardCommand(this);
            cmd.setExecutor(gc);
            cmd.setTabCompleter(gc);
        }

        if (discord.isEnabled()) {
            discord.send("🛡️ **ServerGuardX v" + getDescription().getVersion() +
                    "** enabled on `" + Bukkit.getServer().getName() + "`");
        }

        getLogger().info("All systems online");
    }

    @Override
    public void onDisable() {
        if (webDashboard != null) webDashboard.stop();
        if (pluginProfiler != null) pluginProfiler.stop();
        if (gcMonitor != null) gcMonitor.stop();
        if (tickProfiler != null) tickProfiler.stop();
        if (sampler != null) sampler.stop();
        if (guardLogger != null) guardLogger.close();
        getLogger().info("ServerGuardX disabled.");
    }

    private void printBanner() {
        String[] lines = {
            "",
            "================================================================",
            "",
            "       S E R V E R   G U A R D   X",
            "",
            "       Version " + getDescription().getVersion() + " | Zero-Impact Diagnostics",
            "",
            "================================================================",
            "  Type /sg help for commands.",
            ""
        };
        for (String line : lines) {
            getServer().getConsoleSender().sendMessage(line);
        }
    }

    public static ServerGuardX getInstance() { return instance; }
    public GuardLogger logs() { return guardLogger; }
    public DiscordNotifier discord() { return discord; }
    public TickProfiler ticks() { return tickProfiler; }
    public Sampler samples() { return sampler; }
    public LagAnalyzer analyzer() { return analyzer; }
    public BaselineTracker baseline() { return baseline; }
    public PluginProfiler profiler() { return pluginProfiler; }
    public GCMonitor gc() { return gcMonitor; }
}

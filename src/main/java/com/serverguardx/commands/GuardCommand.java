private void plugins(CommandSender s) {
    s.sendMessage(ChatColor.GOLD + "╔══ Plugin List ══╗");
    s.sendMessage(ChatColor.YELLOW + "Enabled: " + ChatColor.WHITE +
            plugin.profiler().getEnabledPluginCount());

    List<PluginProfiler.PluginReport> reports = plugin.profiler().getReports();
    int shown = 0;
    for (var r : reports) {
        if (shown++ >= 20) break;
        ChatColor state = r.enabled ? ChatColor.GREEN : ChatColor.RED;
        String taskInfo = r.activeTasks >= 0 ? " (" + r.activeTasks + " tasks)" : "";
        s.sendMessage(String.format("%s%-3s %s%-20s %sv%s%s",
                ChatColor.GRAY, r.enabled ? "✓" : "✗",
                ChatColor.WHITE, r.name,
                ChatColor.DARK_GRAY, r.version,
                ChatColor.GRAY, taskInfo));
    }
    s.sendMessage(ChatColor.GOLD + "╚═════════════════╝");
}

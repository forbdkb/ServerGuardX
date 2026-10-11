package com.serverguardx.notify;

import com.serverguardx.ServerGuardX;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiscordNotifier {

    private final ServerGuardX plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ServerGuardX-Discord");
        t.setDaemon(true);
        return t;
    });

    public DiscordNotifier(ServerGuardX plugin) { this.plugin = plugin; }

    public boolean isEnabled() {
        String url = plugin.getConfig().getString("alerts.discord-webhook", "");
        return url != null && !url.isEmpty();
    }

    public void send(String content) {
        if (!isEnabled()) return;
        String url = plugin.getConfig().getString("alerts.discord-webhook");
        executor.submit(() -> {
            try {
                String json = "{\"content\":\"" + escape(content) + "\"}";
                HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        });
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}

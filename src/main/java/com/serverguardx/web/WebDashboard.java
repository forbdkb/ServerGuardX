package com.serverguardx.web;

import com.serverguardx.ServerGuardX;
import com.serverguardx.core.Sampler;
import com.serverguardx.core.TickProfiler;
import com.serverguardx.profiler.GCMonitor;
import com.serverguardx.profiler.PluginProfiler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class WebDashboard {

    private final ServerGuardX plugin;
    private HttpServer server;

    public WebDashboard(ServerGuardX plugin) {
        this.plugin = plugin;
    }

    public void start() throws IOException {
        int port = plugin.getConfig().getInt("web.port", 8123);
        String bind = plugin.getConfig().getString("web.bind-address", "127.0.0.1");

        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/", this::handleIndex);
        server.createContext("/api/stats", this::handleApi);
        server.setExecutor(null);
        server.start();

        plugin.logs().log("WEB", "Dashboard: http://" + bind + ":" + port);
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private void handleIndex(HttpExchange ex) throws IOException {
        String html = buildHtml();
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleApi(HttpExchange ex) throws IOException {
        String json = buildJson();
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String buildHtml() {
        return """
        <!DOCTYPE html>
        <html lang="ar">
        <head>
          <meta charset="UTF-8">
          <title>ServerGuardX Dashboard</title>
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <style>
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body { font-family: 'Segoe UI', Arial, sans-serif; background: #0d1117; color: #c9d1d9; padding: 20px; }
            h1 { color: #58a6ff; margin-bottom: 20px; text-align: center; }
            .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 15px; margin-bottom: 20px; }
            .card { background: #161b22; border: 1px solid #30363d; border-radius: 10px; padding: 18px; }
            .card h3 { color: #8b949e; font-size: 13px; text-transform: uppercase; margin-bottom: 10px; letter-spacing: 1px; }
            .card .value { font-size: 32px; font-weight: bold; color: #58a6ff; }
            .card .value.good { color: #3fb950; }
            .card .value.warn { color: #d29922; }
            .card .value.bad { color: #f85149; }
            .section { background: #161b22; border: 1px solid #30363d; border-radius: 10px; padding: 18px; margin-bottom: 15px; }
            .section h2 { color: #58a6ff; margin-bottom: 12px; font-size: 16px; }
            .row { display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px solid #21262d; font-size: 14px; }
            .row:last-child { border-bottom: none; }
            .row .name { color: #c9d1d9; }
            .row .val { color: #58a6ff; font-weight: bold; }
            .footer { text-align: center; color: #8b949e; font-size: 12px; margin-top: 20px; }
            #status { text-align: center; color: #3fb950; font-size: 12px; margin-bottom: 10px; }
          </style>
        </head>
        <body>
          <h1>🛡️ ServerGuardX Dashboard</h1>
          <div id="status">● Live — auto-refresh every 5s</div>
          <div class="grid" id="grid"></div>
          <div class="section"><h2>📊 Tick Performance</h2><div id="ticks"></div></div>
          <div class="section"><h2>🗑️ Garbage Collector</h2><div id="gc"></div></div>
          <div class="section"><h2>🔌 Plugins</h2><div id="plugins"></div></div>
          <div class="footer">ServerGuardX v3.0 · Zero Impact</div>
          <script>
            async function refresh() {
              try {
                const r = await fetch('/api/stats');
                const d = await r.json();
                renderGrid(d);
                renderTicks(d);
                renderGC(d);
                renderPlugins(d);
                document.getElementById('status').style.color = '#3fb950';
                document.getElementById('status').textContent = '● Live — updated ' + new Date().toLocaleTimeString();
              } catch (e) {
                document.getElementById('status').style.color = '#f85149';
                document.getElementById('status').textContent = '● Disconnected';
              }
            }
            function renderGrid(d) {
              const msptClass = d.mspt < 50 ? 'good' : d.mspt < 100 ? 'warn' : 'bad';
              const ramClass = d.ramPercent < 75 ? 'good' : d.ramPercent < 90 ? 'warn' : 'bad';
              const pingClass = d.avgPing < 80 ? 'good' : d.avgPing < 150 ? 'warn' : 'bad';
              document.getElementById('grid').innerHTML = `
                <div class="card"><h3>MSPT</h3><div class="value ${msptClass}">${d.mspt.toFixed(2)}</div></div>
                <div class="card"><h3>TPS (est)</h3><div class="value ${msptClass}">${d.tps.toFixed(1)}</div></div>
                <div class="card"><h3>Players</h3><div class="value">${d.players}</div></div>
                <div class="card"><h3>Avg Ping</h3><div class="value ${pingClass}">${d.avgPing}ms</div></div>
                <div class="card"><h3>RAM</h3><div class="value ${ramClass}">${d.ramPercent}%</div></div>
                <div class="card"><h3>Entities</h3><div class="value">${d.entities}</div></div>
                <div class="card"><h3>Chunks</h3><div class="value">${d.chunks}</div></div>
                <div class="card"><h3>Plugins</h3><div class="value">${d.pluginCount}</div></div>
              `;
            }
            function renderTicks(d) {
              document.getElementById('ticks').innerHTML = `
                <div class="row"><span class="name">Current MSPT</span><span class="val">${d.mspt.toFixed(2)} ms</span></div>
                <div class="row"><span class="name">Average MSPT</span><span class="val">${d.avgMspt.toFixed(2)} ms</span></div>
                <div class="row"><span class="name">P95 MSPT</span><span class="val">${d.p95Mspt.toFixed(2)} ms</span></div>
                <div class="row"><span class="name">Effective TPS</span><span class="val">${d.tps.toFixed(2)}</span></div>
              `;
            }
            function renderGC(d) {
              document.getElementById('gc').innerHTML = `
                <div class="row"><span class="name">GC Type</span><span class="val">${d.gcType}</span></div>
                <div class="row"><span class="name">Health</span><span class="val">${d.gcHealth}</span></div>
                <div class="row"><span class="name">Collections/min</span><span class="val">${d.gcPerMin.toFixed(1)}</span></div>
                <div class="row"><span class="name">Max Pause</span><span class="val">${d.gcMaxPause} ms</span></div>
                <div class="row"><span class="name">Total Events</span><span class="val">${d.gcTotal}</span></div>
              `;
            }
            function renderPlugins(d) {
              let html = '';
              d.plugins.slice(0, 20).forEach(p => {
                html += `<div class="row"><span class="name">${p.name} <small style="color:#8b949e">v${p.version}</small></span><span class="val">${p.enabled ? '✓ ' + p.tasks + ' tasks' : '✗ disabled'}</span></div>`;
              });
              document.getElementById('plugins').innerHTML = html || '<div class="row"><span class="name">No plugins</span></div>';
            }
            refresh();
            setInterval(refresh, 5000);
          </script>
        </body>
        </html>
        """;
    }

    private String buildJson() {
        TickProfiler tp = plugin.ticks();
        Sampler.Snapshot snap = plugin.samples().getLatest();
        GCMonitor.GCStats gc = plugin.gc().getStats();
        List<PluginProfiler.PluginReport> plugins = plugin.profiler().getReports();

        StringBuilder json = new StringBuilder();
        json.append("{");

        json.append("\"mspt\":").append(tp.getLastMspt()).append(",");
        json.append("\"avgMspt\":").append(tp.getAverageMspt()).append(",");
        json.append("\"p95Mspt\":").append(tp.getP95Mspt()).append(",");
        json.append("\"tps\":").append(Math.min(20.0, 1000.0 / Math.max(1, tp.getAverageMspt()))).append(",");

        if (snap != null) {
            json.append("\"players\":").append(snap.playerCount).append(",");
            json.append("\"avgPing\":").append(snap.avgPing).append(",");
            json.append("\"maxPing\":").append(snap.maxPing).append(",");
            json.append("\"entities\":").append(snap.totalEntities).append(",");
            json.append("\"chunks\":").append(snap.totalChunks).append(",");
            json.append("\"ramPercent\":").append(snap.heapMax > 0 ? (snap.heapUsed * 100 / snap.heapMax) : 0).append(",");
        } else {
            json.append("\"players\":0,\"avgPing\":0,\"maxPing\":0,\"entities\":0,\"chunks\":0,\"ramPercent\":0,");
        }

        json.append("\"gcType\":\"").append(gc.gcType).append("\",");
        json.append("\"gcHealth\":\"").append(gc.health).append("\",");
        json.append("\"gcPerMin\":").append(gc.gcPerMinute).append(",");
        json.append("\"gcMaxPause\":").append(gc.maxPauseMs).append(",");
        json.append("\"gcTotal\":").append(gc.totalCount).append(",");

        json.append("\"pluginCount\":").append(plugin.profiler().getEnabledPluginCount()).append(",");

        json.append("\"plugins\":[");
        for (int i = 0; i < plugins.size(); i++) {
            PluginProfiler.PluginReport p = plugins.get(i);
            if (i > 0) json.append(",");
            json.append("{");
            json.append("\"name\":\"").append(escape(p.name)).append("\",");
            json.append("\"version\":\"").append(escape(p.version)).append("\",");
            json.append("\"enabled\":").append(p.enabled).append(",");
            json.append("\"tasks\":").append(p.activeTasks < 0 ? 0 : p.activeTasks);
            json.append("}");
        }
        json.append("]");

        json.append("}");
        return json.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

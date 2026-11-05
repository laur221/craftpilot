package dev.kridder.craftpilot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public final class ApiServer {
	private static final int MAX_BODY = 1024 * 1024;
	private static final Gson GSON = new Gson();

	private final CraftPilotConfig cfg;
	private HttpServer http;
	private ExecutorService pool;

	public ApiServer(CraftPilotConfig cfg) {
		this.cfg = cfg;
	}

	public void start() throws IOException {
		http = HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), cfg.port), 16);
		AtomicInteger n = new AtomicInteger();
		pool = Executors.newFixedThreadPool(4, r -> {
			Thread t = new Thread(r, "CraftPilot-HTTP-" + n.incrementAndGet());
			t.setDaemon(true);
			return t;
		});
		http.setExecutor(pool);
		http.createContext("/api/", this::handle);
		http.start();
	}

	public void stop() {
		try {
			if (http != null) http.stop(0);
		} finally {
			if (pool != null) pool.shutdownNow();
		}
	}

	private boolean authorized(HttpExchange ex) {
		String h = ex.getRequestHeaders().getFirst("Authorization");
		if (h == null) return false;
		String prefix = "Bearer ";
		if (h.length() < prefix.length() || !h.regionMatches(true, 0, prefix, 0, prefix.length())) return false;
		String given = h.substring(prefix.length()).trim();
		return MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), cfg.token.getBytes(StandardCharsets.UTF_8));
	}

	private void handle(HttpExchange ex) throws IOException {
		try {
			if (!authorized(ex)) {
				JsonObject o = new JsonObject();
				o.addProperty("ok", false);
				o.addProperty("error", "unauthorized");
				send(ex, 401, o);
				return;
			}
			if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
				send(ex, 200, err("use POST"));
				return;
			}
			String path = ex.getRequestURI().getPath();
			String name = path.startsWith("/api/") ? path.substring(5) : path;
			byte[] body;
			try {
				body = readBody(ex.getRequestBody());
			} catch (IOException tooBig) {
				send(ex, 200, err("request body too large (max 1 MB)"));
				return;
			}
			JsonObject args = new JsonObject();
			if (body.length > 0) {
				String s = new String(body, StandardCharsets.UTF_8).trim();
				if (!s.isEmpty()) {
					JsonElement el = JsonParser.parseString(s);
					if (el.isJsonObject()) args = el.getAsJsonObject();
				}
			}
			JsonObject result;
			try {
				if ("capabilities".equals(name)) {
					result = capabilities();
				} else if (CraftPilotConfig.COMMANDS.contains(toggleOf(name)) && !cfg.isEnabled(toggleOf(name))) {
					result = err("'" + name + "' is disabled in CraftPilot settings");
				} else {
					result = Actions.dispatch(name, args);
				}
			} catch (Throwable t) {
				Throwable c = t;
				while (c.getCause() != null && (c instanceof java.util.concurrent.ExecutionException)) c = c.getCause();
				result = err(c instanceof java.util.concurrent.TimeoutException ? "timed out waiting for game thread"
						: (c.getMessage() != null ? c.getMessage() : c.toString()));
			}
			send(ex, 200, result);
		} catch (Throwable t) {
			try {
				send(ex, 200, err(String.valueOf(t.getMessage())));
			} catch (Throwable ignored) {
			}
		} finally {
			ex.close();
		}
	}

	/** goto_status and goto_stop share the 'goto' toggle. */
	private static String toggleOf(String name) {
		return name.startsWith("goto_") ? "goto" : name;
	}

	private JsonObject capabilities() {
		JsonArray en = new JsonArray();
		JsonArray dis = new JsonArray();
		for (String n : CraftPilotConfig.COMMANDS) (cfg.isEnabled(n) ? en : dis).add(n);
		JsonObject o = new JsonObject();
		o.addProperty("ok", true);
		o.add("enabled", en);
		o.add("disabled", dis);
		return o;
	}

	private static byte[] readBody(InputStream in) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int r;
		while ((r = in.read(buf)) > 0) {
			bos.write(buf, 0, r);
			if (bos.size() > MAX_BODY) throw new IOException("too large");
		}
		return bos.toByteArray();
	}

	private static JsonObject err(String m) {
		JsonObject o = new JsonObject();
		o.addProperty("ok", false);
		o.addProperty("error", m);
		return o;
	}

	private static void send(HttpExchange ex, int code, JsonObject o) throws IOException {
		byte[] b = GSON.toJson(o).getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		ex.sendResponseHeaders(code, b.length);
		try (OutputStream os = ex.getResponseBody()) {
			os.write(b);
		}
	}
}

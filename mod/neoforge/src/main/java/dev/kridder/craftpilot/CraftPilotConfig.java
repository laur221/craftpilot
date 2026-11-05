package dev.kridder.craftpilot;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.neoforged.fml.loading.FMLPaths;

/** Config stored in config/craftpilot.json and mirrored to ~/.craftpilot/connection.json. */
public final class CraftPilotConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Every toggleable API endpoint (capabilities is always enabled). */
	public static final List<String> COMMANDS = List.of("status", "screenshot", "screen", "click_widget", "click", "scroll",
			"key", "type", "command", "chat", "chat_log", "move", "look", "hotbar", "use", "attack", "inventory", "world", "respawn", "goto");

	public int port = 25599;
	public String token = "";
	public boolean enabled = true;
	public boolean toastShown = false;
	public boolean keepRunningUnfocused = true;
	public boolean showMenuButton = true;
	public final Map<String, Boolean> commands = new LinkedHashMap<>();

	private static Path path() {
		return FMLPaths.CONFIGDIR.get().resolve("craftpilot.json");
	}

	public boolean isEnabled(String name) {
		Boolean b = commands.get(name);
		return b == null || b;
	}

	public void setEnabled(String name, boolean v) {
		commands.put(name, v);
	}

	public static CraftPilotConfig load() {
		CraftPilotConfig c = new CraftPilotConfig();
		try {
			Path p = path();
			if (Files.exists(p)) {
				JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
				if (o.has("port")) c.port = o.get("port").getAsInt();
				if (o.has("token")) c.token = o.get("token").getAsString();
				if (o.has("enabled")) c.enabled = o.get("enabled").getAsBoolean();
				if (o.has("toastShown")) c.toastShown = o.get("toastShown").getAsBoolean();
				if (o.has("keepRunningUnfocused")) c.keepRunningUnfocused = o.get("keepRunningUnfocused").getAsBoolean();
				if (o.has("showMenuButton")) c.showMenuButton = o.get("showMenuButton").getAsBoolean();
				if (o.has("commands") && o.get("commands").isJsonObject()) {
					for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("commands").entrySet()) {
						try {
							c.commands.put(e.getKey(), e.getValue().getAsBoolean());
						} catch (Exception ignored) {
						}
					}
				}
			}
		} catch (Exception e) {
			CraftPilotClient.LOG.warn("[CraftPilot] could not read config, using defaults", e);
		}
		if (c.token == null || c.token.length() < 16) {
			c.token = newToken();
		}
		c.save();
		return c;
	}

	private static String newToken() {
		byte[] b = new byte[16];
		new SecureRandom().nextBytes(b);
		StringBuilder sb = new StringBuilder();
		for (byte x : b) sb.append(String.format("%02x", x));
		return sb.toString();
	}

	public void resetToken() {
		token = newToken();
		save();
		writeConnectionFile();
	}

	public void save() {
		try {
			Path p = path();
			Files.createDirectories(p.getParent());
			JsonObject o = new JsonObject();
			o.addProperty("port", port);
			o.addProperty("token", token);
			o.addProperty("enabled", enabled);
			o.addProperty("toastShown", toastShown);
			o.addProperty("keepRunningUnfocused", keepRunningUnfocused);
			o.addProperty("showMenuButton", showMenuButton);
			JsonObject cmds = new JsonObject();
			for (String n : COMMANDS) cmds.addProperty(n, isEnabled(n));
			o.add("commands", cmds);
			Files.writeString(p, GSON.toJson(o), StandardCharsets.UTF_8);
		} catch (Exception e) {
			CraftPilotClient.LOG.warn("[CraftPilot] could not write config", e);
		}
	}

	public void writeConnectionFile() {
		try {
			Path dir = Path.of(System.getProperty("user.home"), ".craftpilot");
			Files.createDirectories(dir);
			JsonObject o = new JsonObject();
			o.addProperty("port", port);
			o.addProperty("token", token);
			o.addProperty("gameDir", FMLPaths.GAMEDIR.get().toAbsolutePath().toString());
			Files.writeString(dir.resolve("connection.json"), GSON.toJson(o), StandardCharsets.UTF_8);
		} catch (Exception e) {
			CraftPilotClient.LOG.warn("[CraftPilot] could not write connection.json", e);
		}
	}
}

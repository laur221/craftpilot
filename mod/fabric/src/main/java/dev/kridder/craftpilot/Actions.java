package dev.kridder.craftpilot;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.lwjgl.glfw.GLFW;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.text.Text;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.level.storage.LevelSummary;

/** Implementations of all /api/<name> endpoints. dispatch() runs on an HTTP thread. */
public final class Actions {
	private Actions() {
	}

	// ---------- helpers ----------

	private static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}

	/** Run on the render thread and wait up to 10 seconds. */
	private static <T> T onClient(Callable<T> c) throws Exception {
		CompletableFuture<T> f = mc().submit(() -> {
			try {
				return c.call();
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage() != null ? e.getMessage() : e.toString(), e);
			}
		});
		return f.get(10, TimeUnit.SECONDS);
	}

	private static JsonObject ok() {
		JsonObject o = new JsonObject();
		o.addProperty("ok", true);
		return o;
	}

	private static JsonObject fail(String m) {
		JsonObject o = new JsonObject();
		o.addProperty("ok", false);
		o.addProperty("error", m);
		return o;
	}

	private static String str(JsonObject a, String k, String def) {
		return a.has(k) && !a.get(k).isJsonNull() ? a.get(k).getAsString() : def;
	}

	private static int num(JsonObject a, String k, int def) {
		return a.has(k) && !a.get(k).isJsonNull() ? a.get(k).getAsInt() : def;
	}

	private static double dbl(JsonObject a, String k, double def) {
		return a.has(k) && !a.get(k).isJsonNull() ? a.get(k).getAsDouble() : def;
	}

	private static boolean bool(JsonObject a, String k, boolean def) {
		return a.has(k) && !a.get(k).isJsonNull() ? a.get(k).getAsBoolean() : def;
	}

	private static double round(double d) {
		return Math.round(d * 1000.0) / 1000.0;
	}

	// ---------- dispatch ----------

	public static JsonObject dispatch(String name, JsonObject a) throws Exception {
		switch (name) {
		case "status": return status();
		case "screenshot": return screenshot(a);
		case "screen": return screen();
		case "click_widget": return clickWidget(a);
		case "click": return click(a);
		case "scroll": return scroll(a);
		case "key": return key(a);
		case "type": return type(a);
		case "command": return command(a);
		case "chat": return chat(a);
		case "chat_log": return chatLog(a);
		case "move": return move(a);
		case "look": return look(a);
		case "hotbar": return hotbar(a);
		case "use": return press(a, true);
		case "attack": return press(a, false);
		case "inventory": return inventory();
		case "world": return world(a);
		case "respawn": return respawn();
		case "goto": return gotoStart(a);
		case "goto_status": return onClient(() -> GoTo.status(mc()));
		case "goto_stop": return onClient(() -> GoTo.stopCmd(mc()));
		default: return fail("unknown endpoint: " + name);
		}
	}

	// ---------- status ----------

	private static JsonObject started() {
		JsonObject o = ok();
		o.addProperty("started", true);
		return o;
	}

	private static JsonObject status() throws Exception {
		return onClient(() -> {
			MinecraftClient c = mc();
			JsonObject o = ok();
			o.addProperty("inWorld", c.world != null && c.player != null);
			Screen s = c.currentScreen;
			o.addProperty("screen", s == null ? null : s.getClass().getSimpleName());
			o.addProperty("screenTitle", s == null ? null : s.getTitle().getString());
			if (c.player != null) {
				ClientPlayerEntity p = c.player;
				JsonObject pl = new JsonObject();
				pl.addProperty("name", p.getName().getString());
				pl.addProperty("x", round(p.getX()));
				pl.addProperty("y", round(p.getY()));
				pl.addProperty("z", round(p.getZ()));
				pl.addProperty("yaw", round(p.getYaw()));
				pl.addProperty("pitch", round(p.getPitch()));
				pl.addProperty("health", p.getHealth());
				pl.addProperty("maxHealth", p.getMaxHealth());
				pl.addProperty("food", p.getHungerManager().getFoodLevel());
				pl.addProperty("xpLevel", p.experienceLevel);
				pl.addProperty("dimension", c.world != null ? c.world.getRegistryKey().getValue().toString() : null);
				pl.addProperty("gameMode", c.interactionManager != null && c.interactionManager.getCurrentGameMode() != null
						? c.interactionManager.getCurrentGameMode().getName() : null);
				pl.addProperty("dead", p.isDead() || s instanceof DeathScreen);
				o.add("player", pl);
			} else {
				o.add("player", null);
			}
			o.addProperty("windowWidth", c.getWindow().getWidth());
			o.addProperty("windowHeight", c.getWindow().getHeight());
			o.addProperty("guiScale", c.getWindow().getScaleFactor());
			o.addProperty("scaledWidth", c.getWindow().getScaledWidth());
			o.addProperty("scaledHeight", c.getWindow().getScaledHeight());
			o.addProperty("fps", c.getCurrentFps());
			o.addProperty("mcVersion", "1.21.1");
			o.addProperty("loader", "fabric");
			return o;
		});
	}

	// ---------- screenshot ----------

	private static JsonObject screenshot(JsonObject a) throws Exception {
		int maxW = Math.max(16, num(a, "maxWidth", 1280));
		return onClient(() -> {
			MinecraftClient c = mc();
			NativeImage img = ScreenshotRecorder.takeScreenshot(c.getFramebuffer());
			NativeImage out = img;
			try {
				int w = img.getWidth(), h = img.getHeight();
				int nw = w, nh = h;
				if (w > maxW) {
					nw = maxW;
					nh = Math.max(1, Math.round(h * (maxW / (float) w)));
					out = new NativeImage(nw, nh, false);
					img.resizeSubRectTo(0, 0, w, h, out);
				}
				byte[] png = out.getBytes();
				JsonObject o = ok();
				o.addProperty("png", Base64.getEncoder().encodeToString(png));
				o.addProperty("width", nw);
				o.addProperty("height", nh);
				return o;
			} finally {
				if (out != img) out.close();
				img.close();
			}
		});
	}

	// ---------- screen / widgets ----------

	private static void collect(Element e, List<ClickableWidget> out, Set<Object> seen) {
		if (e == null || !seen.add(e)) return;
		if (e instanceof ClickableWidget w) out.add(w);
		if (e instanceof ParentElement p) {
			for (Element ch : new ArrayList<Element>(p.children())) collect(ch, out, seen);
		}
	}

	private static List<ClickableWidget> widgets(Screen s) {
		List<ClickableWidget> out = new ArrayList<>();
		Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (Element e : new ArrayList<Element>(s.children())) collect(e, out, seen);
		return out;
	}

	private static String typeOf(ClickableWidget w) {
		if (w instanceof CheckboxWidget) return "checkbox";
		if (w instanceof TextFieldWidget) return "textfield";
		if (w instanceof SliderWidget) return "slider";
		if (w instanceof PressableWidget) return "button";
		return "other";
	}

	private static JsonObject screen() throws Exception {
		return onClient(() -> {
			Screen s = mc().currentScreen;
			JsonObject o = ok();
			if (s == null) {
				o.add("screen", null);
				o.add("title", null);
				o.add("widgets", new JsonArray());
				return o;
			}
			o.addProperty("screen", s.getClass().getSimpleName());
			o.addProperty("title", s.getTitle().getString());
			JsonArray arr = new JsonArray();
			int i = 0;
			for (ClickableWidget w : widgets(s)) {
				JsonObject j = new JsonObject();
				j.addProperty("id", i++);
				j.addProperty("type", typeOf(w));
				j.addProperty("label", w.getMessage().getString());
				j.addProperty("x", w.getX());
				j.addProperty("y", w.getY());
				j.addProperty("w", w.getWidth());
				j.addProperty("h", w.getHeight());
				j.addProperty("active", w.active);
				j.addProperty("visible", w.visible);
				j.addProperty("focused", w.isFocused());
				if (w instanceof TextFieldWidget t) j.addProperty("value", t.getText());
				if (w instanceof CheckboxWidget cb) j.addProperty("checked", cb.isChecked());
				arr.add(j);
			}
			o.add("widgets", arr);
			return o;
		});
	}

	private static void clickAt(Screen s, double x, double y, int button) {
		s.mouseClicked(x, y, button);
		s.mouseReleased(x, y, button);
	}

	private static JsonObject clickWidget(JsonObject a) throws Exception {
		int button = num(a, "button", 0);
		return onClient(() -> {
			Screen s = mc().currentScreen;
			if (s == null) return fail("no screen open");
			List<ClickableWidget> ws = widgets(s);
			ClickableWidget target = null;
			int idx = -1;
			if (a.has("id") && !a.get("id").isJsonNull()) {
				idx = a.get("id").getAsInt();
				if (idx < 0 || idx >= ws.size()) return fail("no widget with id " + idx);
				target = ws.get(idx);
			} else if (a.has("label")) {
				String want = a.get("label").getAsString().toLowerCase(Locale.ROOT);
				for (int pass = 0; pass < 2 && target == null; pass++) {
					for (int i = 0; i < ws.size(); i++) {
						ClickableWidget w = ws.get(i);
						if (!w.visible) continue;
						String l = w.getMessage().getString().toLowerCase(Locale.ROOT);
						if (pass == 0 ? l.equals(want) : l.contains(want)) {
							target = w;
							idx = i;
							break;
						}
					}
				}
				if (target == null) return fail("no widget matching label '" + a.get("label").getAsString() + "'");
			} else {
				return fail("need 'label' or 'id'");
			}
			double cx = target.getX() + target.getWidth() / 2.0;
			double cy = target.getY() + target.getHeight() / 2.0;
			clickAt(s, cx, cy, button);
			JsonObject o = ok();
			o.addProperty("id", idx);
			o.addProperty("label", target.getMessage().getString());
			o.addProperty("x", cx);
			o.addProperty("y", cy);
			return o;
		});
	}

	private static JsonObject click(JsonObject a) throws Exception {
		double x = dbl(a, "x", 0), y = dbl(a, "y", 0);
		int button = num(a, "button", 0);
		return onClient(() -> {
			Screen s = mc().currentScreen;
			if (s == null) return fail("no screen open");
			clickAt(s, x, y, button);
			return ok();
		});
	}

	private static JsonObject scroll(JsonObject a) throws Exception {
		double x = dbl(a, "x", 0), y = dbl(a, "y", 0), amt = dbl(a, "amount", -1);
		return onClient(() -> {
			Screen s = mc().currentScreen;
			if (s == null) return fail("no screen open");
			s.mouseScrolled(x, y, 0.0, amt);
			return ok();
		});
	}

	// ---------- keyboard ----------

	private static int keyCode(String name) {
		String n = name.toLowerCase(Locale.ROOT).trim();
		switch (n) {
		case "enter": case "return": return GLFW.GLFW_KEY_ENTER;
		case "esc": case "escape": return GLFW.GLFW_KEY_ESCAPE;
		case "up": return GLFW.GLFW_KEY_UP;
		case "down": return GLFW.GLFW_KEY_DOWN;
		case "left": return GLFW.GLFW_KEY_LEFT;
		case "right": return GLFW.GLFW_KEY_RIGHT;
		case "tab": return GLFW.GLFW_KEY_TAB;
		case "backspace": return GLFW.GLFW_KEY_BACKSPACE;
		case "delete": case "del": return GLFW.GLFW_KEY_DELETE;
		case "space": return GLFW.GLFW_KEY_SPACE;
		case "shift": case "lshift": return GLFW.GLFW_KEY_LEFT_SHIFT;
		case "ctrl": case "control": case "lctrl": return GLFW.GLFW_KEY_LEFT_CONTROL;
		case "alt": case "lalt": return GLFW.GLFW_KEY_LEFT_ALT;
		case "home": return GLFW.GLFW_KEY_HOME;
		case "end": return GLFW.GLFW_KEY_END;
		case "pageup": return GLFW.GLFW_KEY_PAGE_UP;
		case "pagedown": return GLFW.GLFW_KEY_PAGE_DOWN;
		case "insert": return GLFW.GLFW_KEY_INSERT;
		default: break;
		}
		if (n.matches("f([1-9]|1[0-9]|2[0-5])")) return GLFW.GLFW_KEY_F1 + Integer.parseInt(n.substring(1)) - 1;
		try {
			InputUtil.Key k = InputUtil.fromTranslationKey("key.keyboard." + n);
			if (k != InputUtil.UNKNOWN_KEY) return k.getCode();
		} catch (Exception ignored) {
		}
		throw new IllegalArgumentException("unknown key: " + name);
	}

	private static JsonObject key(JsonObject a) throws Exception {
		String name = str(a, "key", null);
		if (name == null) return fail("missing 'key'");
		int code = keyCode(name);
		String action = str(a, "action", "press").toLowerCase(Locale.ROOT);
		int mods = num(a, "mods", 0);
		return onClient(() -> {
			MinecraftClient c = mc();
			long h = c.getWindow().getHandle();
			int sc = GLFW.glfwGetKeyScancode(code);
			switch (action) {
			case "press" -> {
				c.keyboard.onKey(h, code, sc, GLFW.GLFW_PRESS, mods);
				c.keyboard.onKey(h, code, sc, GLFW.GLFW_RELEASE, mods);
			}
			case "down" -> c.keyboard.onKey(h, code, sc, GLFW.GLFW_PRESS, mods);
			case "up" -> c.keyboard.onKey(h, code, sc, GLFW.GLFW_RELEASE, mods);
			default -> {
				return fail("action must be press, down or up");
			}
			}
			return ok();
		});
	}

	private static JsonObject type(JsonObject a) throws Exception {
		String text = str(a, "text", "");
		return onClient(() -> {
			MinecraftClient c = mc();
			Screen s = c.currentScreen;
			if (s == null) return fail("no screen open (typing is only supported into screens)");
			text.codePoints().forEach(cp -> {
				for (char ch : Character.toChars(cp)) s.charTyped(ch, 0);
			});
			return ok();
		});
	}

	// ---------- chat / commands ----------

	private static JsonObject command(JsonObject a) throws Exception {
		String cmd = str(a, "command", null);
		if (cmd == null) return fail("missing 'command'");
		if (cmd.startsWith("/")) cmd = cmd.substring(1);
		final String fcmd = cmd;
		long t0 = System.currentTimeMillis();
		JsonObject r = onClient(() -> {
			ClientPlayerEntity p = mc().player;
			if (p == null) return fail("not in a world");
			String root = fcmd.split("\\s+", 2)[0];
			com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> d = net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.getActiveDispatcher();
			if (d != null && d.getRoot().getChild(root) != null) {
				try {
					d.execute(fcmd, (net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource) mc().getNetworkHandler().getCommandSource());
				} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
					ChatLog.add(e.getMessage());
				}
				return ok();
			}
			p.networkHandler.sendChatCommand(fcmd);
			return ok();
		});
		if (!r.get("ok").getAsBoolean()) return r;
		Thread.sleep(700);
		JsonArray out = new JsonArray();
		for (String s : ChatLog.since(t0)) out.add(s);
		r.add("output", out);
		return r;
	}

	private static JsonObject chat(JsonObject a) throws Exception {
		String msg = str(a, "message", null);
		if (msg == null) return fail("missing 'message'");
		return onClient(() -> {
			ClientPlayerEntity p = mc().player;
			if (p == null) return fail("not in a world");
			p.networkHandler.sendChatMessage(msg);
			return ok();
		});
	}

	private static JsonObject chatLog(JsonObject a) {
		JsonObject o = ok();
		JsonArray arr = new JsonArray();
		for (String s : ChatLog.last(num(a, "limit", 30))) arr.add(s);
		o.add("lines", arr);
		return o;
	}

	// ---------- movement ----------

	private static JsonObject move(JsonObject a) throws Exception {
		int ticks = Math.max(1, num(a, "ticks", 20));
		return onClient(() -> {
			MinecraftClient c = mc();
			var o = c.options;
			if (bool(a, "forward", false)) KeyHold.hold(o.forwardKey, ticks, false);
			if (bool(a, "back", false)) KeyHold.hold(o.backKey, ticks, false);
			if (bool(a, "left", false)) KeyHold.hold(o.leftKey, ticks, false);
			if (bool(a, "right", false)) KeyHold.hold(o.rightKey, ticks, false);
			if (bool(a, "jump", false)) KeyHold.hold(o.jumpKey, ticks, false);
			if (bool(a, "sneak", false)) KeyHold.hold(o.sneakKey, ticks, false);
			if (bool(a, "sprint", false)) KeyHold.hold(o.sprintKey, ticks, false);
			JsonObject r = ok();
			r.addProperty("ticks", ticks);
			return r;
		});
	}

	private static JsonObject look(JsonObject a) throws Exception {
		return onClient(() -> {
			ClientPlayerEntity p = mc().player;
			if (p == null) return fail("not in a world");
			float yaw = p.getYaw(), pitch = p.getPitch();
			if (a.has("yaw")) yaw = (float) dbl(a, "yaw", yaw);
			if (a.has("pitch")) pitch = (float) dbl(a, "pitch", pitch);
			if (a.has("dyaw")) yaw += (float) dbl(a, "dyaw", 0);
			if (a.has("dpitch")) pitch += (float) dbl(a, "dpitch", 0);
			pitch = Math.max(-90f, Math.min(90f, pitch));
			p.setYaw(yaw);
			p.setPitch(pitch);
			p.setHeadYaw(yaw);
			p.setBodyYaw(yaw);
			JsonObject o = ok();
			o.addProperty("yaw", round(yaw));
			o.addProperty("pitch", round(pitch));
			return o;
		});
	}

	private static JsonObject hotbar(JsonObject a) throws Exception {
		int slot = num(a, "slot", -1);
		if (slot < 0 || slot > 8) return fail("slot must be 0..8");
		return onClient(() -> {
			ClientPlayerEntity p = mc().player;
			if (p == null) return fail("not in a world");
			p.getInventory().selectedSlot = slot;
			return ok();
		});
	}

	private static JsonObject press(JsonObject a, boolean use) throws Exception {
		int ticks = Math.max(1, num(a, "ticks", 1));
		return onClient(() -> {
			MinecraftClient c = mc();
			if (c.player == null) return fail("not in a world");
			KeyBinding kb = use ? c.options.useKey : c.options.attackKey;
			KeyHold.hold(kb, ticks, true);
			JsonObject r = ok();
			r.addProperty("ticks", ticks);
			return r;
		});
	}

	// ---------- inventory ----------

	private static void addSlot(JsonArray arr, int slot, ItemStack st) {
		if (st == null || st.isEmpty()) return;
		JsonObject j = new JsonObject();
		j.addProperty("slot", slot);
		j.addProperty("item", Registries.ITEM.getId(st.getItem()).toString());
		j.addProperty("count", st.getCount());
		j.addProperty("name", st.getName().getString());
		arr.add(j);
	}

	private static JsonObject inventory() throws Exception {
		return onClient(() -> {
			ClientPlayerEntity p = mc().player;
			if (p == null) return fail("not in a world");
			JsonArray arr = new JsonArray();
			var inv = p.getInventory();
			for (int i = 0; i < inv.main.size(); i++) addSlot(arr, i, inv.main.get(i));
			for (int i = 0; i < inv.armor.size(); i++) addSlot(arr, 36 + i, inv.armor.get(i));
			for (int i = 0; i < inv.offHand.size(); i++) addSlot(arr, 40 + i, inv.offHand.get(i));
			JsonObject o = ok();
			o.addProperty("selectedSlot", inv.selectedSlot);
			o.add("items", arr);
			return o;
		});
	}

	// ---------- world ----------

	private static JsonObject world(JsonObject a) throws Exception {
		String action = str(a, "action", "list").toLowerCase(Locale.ROOT);
		switch (action) {
		case "list": {
			LevelStorage storage = mc().getLevelStorage();
			LevelStorage.LevelList list = storage.getLevelList();
			List<LevelSummary> sums = storage.loadSummaries(list).get(10, TimeUnit.SECONDS);
			JsonArray arr = new JsonArray();
			for (LevelSummary s : sums) {
				JsonObject j = new JsonObject();
				j.addProperty("name", s.getName());
				j.addProperty("displayName", s.getDisplayName());
				j.addProperty("lastPlayed", s.getLastPlayed());
				j.addProperty("gameMode", s.getGameMode().getName());
				j.addProperty("cheats", s.hasCheats());
				j.addProperty("locked", s.isLocked());
				arr.add(j);
			}
			JsonObject o = ok();
			o.add("worlds", arr);
			return o;
		}
		case "open": {
			String name = str(a, "name", null);
			if (name == null) return fail("missing 'name'");
			mc().execute(() -> {
				try { mc().createIntegratedServerLoader().start(name, () -> {}); }
				catch (Throwable t) { CraftPilotClient.LOG.warn("[CraftPilot] world open failed", t); }
			});
			return started();
		}
		case "create": {
			String name = str(a, "name", null);
			if (name == null) return fail("missing 'name'");
			GameMode gm = "creative".equalsIgnoreCase(str(a, "gameMode", "survival")) ? GameMode.CREATIVE : GameMode.SURVIVAL;
			boolean cheats = bool(a, "cheats", false);
			mc().execute(() -> {
				try {
					MinecraftClient c = mc();
					LevelInfo info = new LevelInfo(name, gm, false, Difficulty.NORMAL, cheats, new GameRules(),
							DataConfiguration.SAFE_MODE);
					c.createIntegratedServerLoader().createAndStart(name, info, GeneratorOptions.createRandom(),
							WorldPresets::createDemoOptions, c.currentScreen != null ? c.currentScreen : new TitleScreen());
				} catch (Throwable t) {
					CraftPilotClient.LOG.warn("[CraftPilot] world create failed", t);
				}
			});
			return started();
		}
		case "leave": {
			return onClient(() -> {
				MinecraftClient c = mc();
				if (c.world == null) return fail("not in a world");
				boolean sp = c.isInSingleplayer();
				c.world.disconnect();
				if (sp) c.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
				else c.disconnect();
				c.setScreen(new TitleScreen());
				return ok();
			});
		}
		default:
			return fail("unknown world action: " + action);
		}
	}

	private static JsonObject gotoStart(JsonObject a) throws Exception {
		if (!a.has("x") || !a.has("y") || !a.has("z")) return fail("need 'x', 'y' and 'z'");
		double x = dbl(a, "x", 0), y = dbl(a, "y", 0), z = dbl(a, "z", 0);
		int maxDist = num(a, "maxDistance", 128);
		boolean sprint = bool(a, "sprint", false);
		return onClient(() -> GoTo.start(mc(), x, y, z, maxDist, sprint));
	}

	private static JsonObject respawn() throws Exception {
		return onClient(() -> {
			MinecraftClient c = mc();
			if (c.player == null) return fail("not in a world");
			c.player.requestRespawn();
			c.setScreen(null);
			return ok();
		});
	}
}

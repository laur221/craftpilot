package dev.kridder.craftpilot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

public class CraftPilotClient implements ClientModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("craftpilot");
	private static ApiServer server;
	private static CraftPilotConfig current;
	private static final String SETUP_SNIPPET = "{\"mcpServers\":{\"craftpilot\":{\"command\":\"npx\",\"args\":[\"-y\",\"github:laur221/craftpilot\"]}}}";

	@Override
	public void onInitializeClient() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) ChatLog.add(message.getString());
		});
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, ts) -> ChatLog.add(message.getString()));
		ClientTickEvents.END_CLIENT_TICK.register(KeyHold::tick);
		ClientTickEvents.END_CLIENT_TICK.register(GoTo::tick);
		PauseMenuButton.register();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("craftpilot")
				.then(ClientCommandManager.literal("setup").executes(ctx -> {
					copySetup();
					ctx.getSource().sendFeedback(Text.translatable("craftpilot.setup.copied"));
					return Command.SINGLE_SUCCESS;
				}))
				.then(ClientCommandManager.literal("config").executes(ctx -> {
					// the chat screen closes after the command runs, so open on the next tick
					mc().send(() -> mc().setScreen(new CraftPilotConfigScreen(null)));
					return Command.SINGLE_SUCCESS;
				}))
				.then(ClientCommandManager.literal("status").executes(ctx -> {
					CraftPilotConfig c = config();
					ctx.getSource().sendFeedback(Text.literal("API on 127.0.0.1:" + c.port + ", enabled=" + c.enabled));
					return Command.SINGLE_SUCCESS;
				}))
				.then(ClientCommandManager.literal("token").then(ClientCommandManager.literal("reset").executes(ctx -> {
					config().resetToken();
					ctx.getSource().sendFeedback(Text.translatable("craftpilot.token.reset"));
					return Command.SINGLE_SUCCESS;
				})))));

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			CraftPilotConfig cfg = CraftPilotConfig.load();
			current = cfg;
			if (cfg.keepRunningUnfocused) client.options.pauseOnLostFocus = false; // runtime only, options.txt untouched
			if (!cfg.enabled) {
				LOG.info("[CraftPilot] disabled in config");
				return;
			}
			if (!startServer()) return;
			if (!cfg.toastShown) {
				cfg.toastShown = true;
				cfg.save();
				client.execute(() -> SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
						Text.translatable("craftpilot.toast.title"),
						Text.translatable("craftpilot.toast.body", String.valueOf(cfg.port))));
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> stopServer());
	}

	/** Current live config (loaded on demand if the game has not started yet). */
	public static synchronized CraftPilotConfig config() {
		if (current == null) current = CraftPilotConfig.load();
		return current;
	}

	public static synchronized boolean startServer() {
		if (server != null) return true;
		CraftPilotConfig cfg = config();
		cfg.writeConnectionFile();
		try {
			ApiServer s = new ApiServer(cfg);
			s.start();
			server = s;
			LOG.info("[CraftPilot] API listening on 127.0.0.1:{}", cfg.port);
			return true;
		} catch (Exception e) {
			LOG.error("[CraftPilot] failed to start API server", e);
			return false;
		}
	}

	public static synchronized void stopServer() {
		if (server != null) {
			server.stop();
			server = null;
			LOG.info("[CraftPilot] API stopped");
		}
	}

	public static void copySetup() {
		mc().keyboard.setClipboard(SETUP_SNIPPET);
	}

	public static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}
}

package dev.kridder.craftpilot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.Command;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

@Mod(value = "craftpilot", dist = Dist.CLIENT)
public class CraftPilotClient {
	public static final Logger LOG = LoggerFactory.getLogger("craftpilot");
	private static ApiServer server;
	private static CraftPilotConfig current;
	private static boolean started = false;
	private static final String SETUP_SNIPPET = "{\"mcpServers\":{\"craftpilot\":{\"command\":\"npx\",\"args\":[\"-y\",\"github:laur221/craftpilot\"]}}}";

	public CraftPilotClient(IEventBus modBus, ModContainer container) {
		if (!FMLEnvironment.dist.isClient()) return;
		container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new CraftPilotConfigScreen(parent));
		NeoForge.EVENT_BUS.register(this);
		NeoForge.EVENT_BUS.register(PauseMenuButton.class);
	}

	@SubscribeEvent
	public void onChat(ClientChatReceivedEvent e) {
		if (!(e instanceof ClientChatReceivedEvent.System s && s.isOverlay())) ChatLog.add(e.getMessage().getString());
	}

	@SubscribeEvent
	public void onTick(ClientTickEvent.Post e) {
		Minecraft client = Minecraft.getInstance();
		if (!started) {
			started = true;
			init(client);
		}
		KeyHold.tick(client);
		GoTo.tick(client);
	}

	@SubscribeEvent
	public void onShutdown(GameShuttingDownEvent e) {
		stopServer();
	}

	@SubscribeEvent
	public void onCommands(RegisterClientCommandsEvent e) {
		e.getDispatcher().register(Commands.literal("craftpilot")
				.then(Commands.literal("setup").executes(ctx -> {
					copySetup();
					ctx.getSource().sendSuccess(() -> Component.translatable("craftpilot.setup.copied"), false);
					return Command.SINGLE_SUCCESS;
				}))
				.then(Commands.literal("config").executes(ctx -> {
					// the chat screen closes after the command runs, so open on the next tick
					mc().tell(() -> mc().setScreen(new CraftPilotConfigScreen(null)));
					return Command.SINGLE_SUCCESS;
				}))
				.then(Commands.literal("status").executes(ctx -> {
					CraftPilotConfig c = config();
					ctx.getSource().sendSuccess(() -> Component.literal("API on 127.0.0.1:" + c.port + ", enabled=" + c.enabled), false);
					return Command.SINGLE_SUCCESS;
				}))
				.then(Commands.literal("token").then(Commands.literal("reset").executes(ctx -> {
					config().resetToken();
					ctx.getSource().sendSuccess(() -> Component.translatable("craftpilot.token.reset"), false);
					return Command.SINGLE_SUCCESS;
				}))));
	}

	private static void init(Minecraft client) {
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
			client.execute(() -> SystemToast.add(client.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
					Component.translatable("craftpilot.toast.title"),
					Component.translatable("craftpilot.toast.body", String.valueOf(cfg.port))));
		}
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
		mc().keyboardHandler.setClipboard(SETUP_SNIPPET);
	}

	public static Minecraft mc() {
		return Minecraft.getInstance();
	}
}

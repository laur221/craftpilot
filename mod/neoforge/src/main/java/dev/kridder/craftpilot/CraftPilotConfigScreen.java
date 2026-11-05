package dev.kridder.craftpilot;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** In-game settings: master switch, focus behaviour and a paged list of per-command toggles. */
public class CraftPilotConfigScreen extends Screen {
	private static final int COL_W = 150;
	private static final int GAP = 6;
	private static final int ROW_H = 24;

	private final Screen parent;
	private final CraftPilotConfig cfg;
	private int page = 0;

	public CraftPilotConfigScreen(Screen parent) {
		super(Component.translatable("craftpilot.config.title"));
		this.parent = parent;
		this.cfg = CraftPilotClient.config();
	}

	private static MutableComponent onOff(Component label, boolean on) {
		return label.copy().append(": ").append(on
				? Component.literal("ON").withStyle(ChatFormatting.GREEN)
				: Component.literal("OFF").withStyle(ChatFormatting.RED));
	}

	@Override
	protected void init() {
		int left = width / 2 - COL_W - GAP / 2;
		int right = width / 2 + GAP / 2;

		Button apiBtn = Button.builder(onOff(Component.translatable("craftpilot.config.api_enabled"), cfg.enabled), b -> {
			cfg.enabled = !cfg.enabled;
			if (cfg.enabled) {
				if (!CraftPilotClient.startServer()) cfg.enabled = false;
			} else {
				CraftPilotClient.stopServer();
			}
			cfg.save();
			b.setMessage(onOff(Component.translatable("craftpilot.config.api_enabled"), cfg.enabled));
		}).bounds(left, 34, COL_W, 20).build();
		addRenderableWidget(apiBtn);

		addRenderableWidget(Button.builder(onOff(Component.translatable("craftpilot.config.keep_running"), cfg.keepRunningUnfocused), b -> {
			cfg.keepRunningUnfocused = !cfg.keepRunningUnfocused;
			if (minecraft != null && minecraft.options != null) {
				// runtime only, options.txt untouched
				if (cfg.keepRunningUnfocused) minecraft.options.pauseOnLostFocus = false;
				else minecraft.options.pauseOnLostFocus = true;
			}
			cfg.save();
			b.setMessage(onOff(Component.translatable("craftpilot.config.keep_running"), cfg.keepRunningUnfocused));
		}).bounds(right, 34, COL_W, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("craftpilot.config.reset_token"), b -> {
			cfg.resetToken();
			b.setMessage(Component.translatable("craftpilot.config.token_reset_done"));
		}).bounds(left, 58, COL_W, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("craftpilot.config.copy_setup"), b -> {
			CraftPilotClient.copySetup();
			b.setMessage(Component.translatable("craftpilot.config.copied"));
		}).bounds(right, 58, COL_W, 20).build());

		addRenderableWidget(Button.builder(onOff(Component.translatable("craftpilot.config.show_menu_button"), cfg.showMenuButton), b -> {
			cfg.showMenuButton = !cfg.showMenuButton;
			cfg.save();
			b.setMessage(onOff(Component.translatable("craftpilot.config.show_menu_button"), cfg.showMenuButton));
		}).bounds(left, 82, COL_W, 20).build());

		// paged command list (two columns)
		int listTop = 110;
		int listBottom = height - 56;
		int rows = Math.max(1, (listBottom - listTop) / ROW_H);
		int perPage = rows * 2;
		List<String> names = CraftPilotConfig.COMMANDS;
		int pages = Math.max(1, (names.size() + perPage - 1) / perPage);
		if (page >= pages) page = pages - 1;
		if (page < 0) page = 0;
		int from = page * perPage;
		for (int i = from; i < Math.min(names.size(), from + perPage); i++) {
			String name = names.get(i);
			int idx = i - from;
			int x = idx % 2 == 0 ? left : right;
			int y = listTop + (idx / 2) * ROW_H;
			Component label = Component.literal(name);
			Button btn = Button.builder(onOff(label, cfg.isEnabled(name)), b -> {
				cfg.setEnabled(name, !cfg.isEnabled(name));
				b.setMessage(onOff(label, cfg.isEnabled(name)));
			}).bounds(x, y, COL_W, 20).tooltip(Tooltip.create(Component.translatable("craftpilot.cmd." + name + ".desc"))).build();
			addRenderableWidget(btn);
		}

		// pager
		int pagerY = height - 52;
		Button prev = Button.builder(Component.translatable("craftpilot.config.prev"), b -> {
			page--;
			rebuildWidgets();
		}).bounds(left, pagerY, 70, 20).build();
		prev.active = page > 0;
		addRenderableWidget(prev);
		Button next = Button.builder(Component.translatable("craftpilot.config.next"), b -> {
			page++;
			rebuildWidgets();
		}).bounds(right + COL_W - 70, pagerY, 70, 20).build();
		next.active = page < pages - 1;
		addRenderableWidget(next);

		// bottom row
		int bw = 100;
		int total = bw * 3 + GAP * 2;
		int bx = width / 2 - total / 2;
		int by = height - 28;
		addRenderableWidget(Button.builder(Component.translatable("craftpilot.config.enable_all"), b -> {
			for (String n : CraftPilotConfig.COMMANDS) cfg.setEnabled(n, true);
			rebuildWidgets();
		}).bounds(bx, by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("craftpilot.config.disable_all"), b -> {
			for (String n : CraftPilotConfig.COMMANDS) cfg.setEnabled(n, false);
			rebuildWidgets();
		}).bounds(bx + bw + GAP, by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("craftpilot.config.done"), b -> onClose())
				.bounds(bx + (bw + GAP) * 2, by, bw, 20).build());
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
		ctx.drawCenteredString(font, Component.translatable("craftpilot.config.api_addr", String.valueOf(cfg.port)), width / 2, 21, 0xAAAAAA);
	}

	@Override
	public void onClose() {
		cfg.save();
		if (minecraft != null) minecraft.setScreen(parent);
	}
}

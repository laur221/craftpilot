package dev.kridder.craftpilot;

import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** In-game settings: master switch, focus behaviour and a paged list of per-command toggles. */
public class CraftPilotConfigScreen extends Screen {
	private static final int COL_W = 150;
	private static final int GAP = 6;
	private static final int ROW_H = 24;

	private final Screen parent;
	private final CraftPilotConfig cfg;
	private int page = 0;

	public CraftPilotConfigScreen(Screen parent) {
		super(Text.translatable("craftpilot.config.title"));
		this.parent = parent;
		this.cfg = CraftPilotClient.config();
	}

	private static MutableText onOff(Text label, boolean on) {
		return label.copy().append(": ").append(on
				? Text.literal("ON").formatted(Formatting.GREEN)
				: Text.literal("OFF").formatted(Formatting.RED));
	}

	@Override
	protected void init() {
		int left = width / 2 - COL_W - GAP / 2;
		int right = width / 2 + GAP / 2;

		ButtonWidget apiBtn = ButtonWidget.builder(onOff(Text.translatable("craftpilot.config.api_enabled"), cfg.enabled), b -> {
			cfg.enabled = !cfg.enabled;
			if (cfg.enabled) {
				if (!CraftPilotClient.startServer()) cfg.enabled = false;
			} else {
				CraftPilotClient.stopServer();
			}
			cfg.save();
			b.setMessage(onOff(Text.translatable("craftpilot.config.api_enabled"), cfg.enabled));
		}).dimensions(left, 34, COL_W, 20).build();
		addDrawableChild(apiBtn);

		addDrawableChild(ButtonWidget.builder(onOff(Text.translatable("craftpilot.config.keep_running"), cfg.keepRunningUnfocused), b -> {
			cfg.keepRunningUnfocused = !cfg.keepRunningUnfocused;
			if (client != null && client.options != null) {
				// runtime only, options.txt untouched
				if (cfg.keepRunningUnfocused) client.options.pauseOnLostFocus = false;
				else client.options.pauseOnLostFocus = true;
			}
			cfg.save();
			b.setMessage(onOff(Text.translatable("craftpilot.config.keep_running"), cfg.keepRunningUnfocused));
		}).dimensions(right, 34, COL_W, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.translatable("craftpilot.config.reset_token"), b -> {
			cfg.resetToken();
			b.setMessage(Text.translatable("craftpilot.config.token_reset_done"));
		}).dimensions(left, 58, COL_W, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.translatable("craftpilot.config.copy_setup"), b -> {
			CraftPilotClient.copySetup();
			b.setMessage(Text.translatable("craftpilot.config.copied"));
		}).dimensions(right, 58, COL_W, 20).build());

		addDrawableChild(ButtonWidget.builder(onOff(Text.translatable("craftpilot.config.show_menu_button"), cfg.showMenuButton), b -> {
			cfg.showMenuButton = !cfg.showMenuButton;
			cfg.save();
			b.setMessage(onOff(Text.translatable("craftpilot.config.show_menu_button"), cfg.showMenuButton));
		}).dimensions(left, 82, COL_W, 20).build());

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
			Text label = Text.literal(name);
			ButtonWidget btn = ButtonWidget.builder(onOff(label, cfg.isEnabled(name)), b -> {
				cfg.setEnabled(name, !cfg.isEnabled(name));
				b.setMessage(onOff(label, cfg.isEnabled(name)));
			}).dimensions(x, y, COL_W, 20).tooltip(Tooltip.of(Text.translatable("craftpilot.cmd." + name + ".desc"))).build();
			addDrawableChild(btn);
		}

		// pager
		int pagerY = height - 52;
		ButtonWidget prev = ButtonWidget.builder(Text.translatable("craftpilot.config.prev"), b -> {
			page--;
			clearAndInit();
		}).dimensions(left, pagerY, 70, 20).build();
		prev.active = page > 0;
		addDrawableChild(prev);
		ButtonWidget next = ButtonWidget.builder(Text.translatable("craftpilot.config.next"), b -> {
			page++;
			clearAndInit();
		}).dimensions(right + COL_W - 70, pagerY, 70, 20).build();
		next.active = page < pages - 1;
		addDrawableChild(next);

		// bottom row
		int bw = 100;
		int total = bw * 3 + GAP * 2;
		int bx = width / 2 - total / 2;
		int by = height - 28;
		addDrawableChild(ButtonWidget.builder(Text.translatable("craftpilot.config.enable_all"), b -> {
			for (String n : CraftPilotConfig.COMMANDS) cfg.setEnabled(n, true);
			clearAndInit();
		}).dimensions(bx, by, bw, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("craftpilot.config.disable_all"), b -> {
			for (String n : CraftPilotConfig.COMMANDS) cfg.setEnabled(n, false);
			clearAndInit();
		}).dimensions(bx + bw + GAP, by, bw, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("craftpilot.config.done"), b -> close())
				.dimensions(bx + (bw + GAP) * 2, by, bw, 20).build());
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);
		ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("craftpilot.config.api_addr", String.valueOf(cfg.port)), width / 2, 21, 0xAAAAAA);
	}

	@Override
	public void close() {
		cfg.save();
		if (client != null) client.setScreen(parent);
	}
}

package dev.kridder.craftpilot;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Small icon button in the top-left corner of the pause menu that opens the CraftPilot settings. */
public final class PauseMenuButton {
	private static final Identifier ICON = Identifier.of("craftpilot", "textures/gui/icon_button.png");
	private static final Identifier ICON_HI = Identifier.of("craftpilot", "textures/gui/icon_button_highlighted.png");

	private PauseMenuButton() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof GameMenuScreen)) return;
			if (!CraftPilotClient.config().showMenuButton) return;
			ButtonWidget btn = ButtonWidget.builder(Text.empty(), b -> client.setScreen(new CraftPilotConfigScreen(screen)))
					.dimensions(4, 4, 20, 20)
					.tooltip(Tooltip.of(Text.translatable("craftpilot.menu.button.tooltip")))
					.build();
			Screens.getButtons(screen).add(btn);
			ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, delta) -> {
				Identifier tex = btn.isHovered() ? ICON_HI : ICON;
				ctx.drawTexture(tex, btn.getX() + 2, btn.getY() + 2, 0f, 0f, 16, 16, 16, 16);
			});
		});
	}
}
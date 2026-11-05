package dev.kridder.craftpilot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Small icon button in the top-left corner of the pause menu that opens the CraftPilot settings. */
public final class PauseMenuButton {
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("craftpilot", "textures/gui/icon_button.png");
	private static final ResourceLocation ICON_HI = ResourceLocation.fromNamespaceAndPath("craftpilot", "textures/gui/icon_button_highlighted.png");

	private PauseMenuButton() {
	}

	private static final class IconButton extends Button {
		IconButton(OnPress press) {
			super(4, 4, 20, 20, Component.empty(), press, DEFAULT_NARRATION);
			setTooltip(Tooltip.create(Component.translatable("craftpilot.menu.button.tooltip")));
		}

		@Override
		public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
			super.renderWidget(g, mouseX, mouseY, delta);
			g.blit(isHoveredOrFocused() ? ICON_HI : ICON, getX() + 2, getY() + 2, 0f, 0f, 16, 16, 16, 16);
		}
	}

	@SubscribeEvent
	public static void onInit(ScreenEvent.Init.Post e) {
		if (!(e.getScreen() instanceof PauseScreen screen)) return;
		if (!CraftPilotClient.config().showMenuButton) return;
		e.addListener(new IconButton(b -> CraftPilotClient.mc().setScreen(new CraftPilotConfigScreen(screen))));
	}
}

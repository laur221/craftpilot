package dev.kridder.craftpilot;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Holds key bindings pressed for N client ticks. Only touched on the render thread. */
public final class KeyHold {
	private static final Map<KeyMapping, Integer> HELD = new LinkedHashMap<>();

	private KeyHold() {
	}

	public static void hold(KeyMapping kb, int ticks, boolean click) {
		ticks = Math.max(1, ticks);
		kb.setDown(true);
		if (click) {
			try {
				KeyMapping.click(InputConstants.getKey(kb.saveString()));
			} catch (Exception ignored) {
			}
		}
		HELD.merge(kb, ticks, Math::max);
	}

	public static void tick(Minecraft client) {
		Iterator<Map.Entry<KeyMapping, Integer>> it = HELD.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<KeyMapping, Integer> e = it.next();
			int left = e.getValue() - 1;
			if (left <= 0) {
				e.getKey().setDown(false);
				it.remove();
			} else {
				e.setValue(left);
			}
		}
	}
}

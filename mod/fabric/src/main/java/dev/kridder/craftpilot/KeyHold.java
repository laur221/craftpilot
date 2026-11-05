package dev.kridder.craftpilot;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/** Holds key bindings pressed for N client ticks. Only touched on the render thread. */
public final class KeyHold {
	private static final Map<KeyBinding, Integer> HELD = new LinkedHashMap<>();

	private KeyHold() {
	}

	public static void hold(KeyBinding kb, int ticks, boolean click) {
		ticks = Math.max(1, ticks);
		kb.setPressed(true);
		if (click) {
			try {
				KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey()));
			} catch (Exception ignored) {
			}
		}
		HELD.merge(kb, ticks, Math::max);
	}

	public static void tick(MinecraftClient client) {
		Iterator<Map.Entry<KeyBinding, Integer>> it = HELD.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<KeyBinding, Integer> e = it.next();
			int left = e.getValue() - 1;
			if (left <= 0) {
				e.getKey().setPressed(false);
				it.remove();
			} else {
				e.setValue(left);
			}
		}
	}
}

package dev.kridder.craftpilot;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;

/** Thread-safe ring buffer (500 lines) of received chat/game messages. */
public final class ChatLog {
	public record Line(long time, String text) {
	}

	private static final int MAX = 500;
	private static final ArrayDeque<Line> LINES = new ArrayDeque<>();

	private ChatLog() {
	}

	private static String lastText;
	private static long lastTime;

	public static synchronized void add(String text) {
		long now = System.currentTimeMillis();
		if (text.equals(lastText) && now - lastTime < 50) return;
		lastText = text;
		lastTime = now;
		LINES.addLast(new Line(System.currentTimeMillis(), text));
		while (LINES.size() > MAX) LINES.removeFirst();
	}

	public static synchronized List<String> last(int n) {
		List<Line> all = new ArrayList<>(LINES);
		int from = Math.max(0, all.size() - Math.max(0, n));
		List<String> out = new ArrayList<>();
		for (int i = from; i < all.size(); i++) out.add(all.get(i).text());
		return out;
	}

	public static synchronized List<String> since(long time) {
		List<String> out = new ArrayList<>();
		for (Line l : LINES) if (l.time() >= time) out.add(l.text());
		return out;
	}
}

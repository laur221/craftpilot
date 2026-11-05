package dev.kridder.craftpilot;

import java.util.List;

import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/** Walks the player along an A* path by driving the movement key bindings. Render thread only. */
public final class GoTo {
	private static final int MAX_REPATHS = 3;
	private static final int STUCK_TICKS = 40;
	private static final float MAX_TURN = 30f;

	private static boolean active = false;
	private static String result = "stopped";
	private static List<BlockPos> path = List.of();
	private static int idx = 0;
	private static BlockPos goal = BlockPos.ZERO;
	private static int maxDistance = 128;
	private static boolean sprint = false;
	private static int repaths = 0;
	private static int stuck = 0;
	private static double bestDist = Double.MAX_VALUE;
	private static int lastIdx = -1;

	private GoTo() {
	}

	private static double round(double d) {
		return Math.round(d * 1000.0) / 1000.0;
	}

	public static JsonObject start(Minecraft c, double x, double y, double z, int maxDist, boolean sprintOn) {
		JsonObject o = new JsonObject();
		if (c.level == null || c.player == null) {
			o.addProperty("ok", false);
			o.addProperty("error", "not in a world");
			return o;
		}
		if (c.screen != null) {
			o.addProperty("ok", false);
			o.addProperty("error", "a screen is open");
			return o;
		}
		if (active) stop(c, "stopped");
		goal = BlockPos.containing(x, y, z);
		maxDistance = Math.max(1, Math.min(128, maxDist));
		sprint = sprintOn;
		repaths = 0;
		List<BlockPos> p = Pathfinder.find(c.level, c.player.blockPosition(), goal, maxDistance);
		if (p == null) {
			o.addProperty("ok", false);
			o.addProperty("error", "no path");
			return o;
		}
		path = p;
		idx = 0;
		stuck = 0;
		bestDist = Double.MAX_VALUE;
		lastIdx = -1;
		active = true;
		result = "running";
		if (p.isEmpty()) stop(c, "arrived");
		o.addProperty("ok", true);
		o.addProperty("pathLength", p.size());
		o.addProperty("started", true);
		return o;
	}

	public static JsonObject status(Minecraft c) {
		JsonObject o = new JsonObject();
		o.addProperty("ok", true);
		o.addProperty("active", active);
		o.addProperty("remaining", active ? Math.max(0, path.size() - idx) : 0);
		if (c.player != null) {
			JsonObject pos = new JsonObject();
			pos.addProperty("x", round(c.player.getX()));
			pos.addProperty("y", round(c.player.getY()));
			pos.addProperty("z", round(c.player.getZ()));
			o.add("position", pos);
		} else {
			o.add("position", null);
		}
		o.addProperty("result", result);
		return o;
	}

	public static JsonObject stopCmd(Minecraft c) {
		boolean was = active;
		if (active) stop(c, "stopped");
		JsonObject o = new JsonObject();
		o.addProperty("ok", true);
		o.addProperty("wasActive", was);
		return o;
	}

	private static void releaseKeys(Minecraft c) {
		if (c.options == null) return;
		Options o = c.options;
		o.keyUp.setDown(false);
		o.keySprint.setDown(false);
		o.keyJump.setDown(false);
	}

	private static void stop(Minecraft c, String res) {
		active = false;
		result = res;
		path = List.of();
		releaseKeys(c);
	}

	public static void tick(Minecraft c) {
		if (!active) return;
		LocalPlayer p = c.player;
		if (c.level == null || p == null) {
			stop(c, "failed:not in a world");
			return;
		}
		if (p.isDeadOrDying() || p.getHealth() <= 0f) {
			stop(c, "failed:player died");
			return;
		}
		if (c.screen != null) {
			stop(c, "failed:screen opened");
			return;
		}
		double px = p.getX(), py = p.getY(), pz = p.getZ();

		// arrived at the goal?
		double gdx = goal.getX() + 0.5 - px, gdz = goal.getZ() + 0.5 - pz;
		if (Math.sqrt(gdx * gdx + gdz * gdz) <= 1.0 && Math.abs(py - goal.getY()) <= 1.5) {
			stop(c, "arrived");
			return;
		}

		// advance waypoints
		while (idx < path.size()) {
			BlockPos n = path.get(idx);
			double dx = n.getX() + 0.5 - px, dz = n.getZ() + 0.5 - pz;
			if (Math.sqrt(dx * dx + dz * dz) < 0.35 && Math.abs(py - n.getY()) < 1.2) idx++;
			else break;
		}
		if (idx >= path.size()) {
			stop(c, "arrived");
			return;
		}
		BlockPos target = path.get(idx);
		double dx = target.getX() + 0.5 - px, dz = target.getZ() + 0.5 - pz;
		double hd = Math.sqrt(dx * dx + dz * dz);

		// stuck detection
		if (idx != lastIdx) {
			lastIdx = idx;
			bestDist = hd;
			stuck = 0;
		} else if (hd < bestDist - 0.02) {
			bestDist = hd;
			stuck = 0;
		} else {
			stuck++;
		}
		if (stuck >= STUCK_TICKS) {
			if (repaths >= MAX_REPATHS) {
				stop(c, "failed:stuck");
				return;
			}
			repaths++;
			List<BlockPos> np = Pathfinder.find(c.level, p.blockPosition(), goal, maxDistance);
			if (np == null) {
				stop(c, "failed:no path after being stuck");
				return;
			}
			path = np;
			idx = 0;
			lastIdx = -1;
			stuck = 0;
			bestDist = Double.MAX_VALUE;
			if (path.isEmpty()) {
				stop(c, "arrived");
				return;
			}
			return;
		}

		// steer
		float want = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
		float diff = Mth.wrapDegrees(want - p.getYRot());
		float step = Mth.clamp(diff, -MAX_TURN, MAX_TURN);
		float yaw = p.getYRot() + step;
		p.setYRot(yaw);
		p.setYHeadRot(yaw);
		p.setYBodyRot(yaw);
		float remaining = Math.abs(diff - step);

		Options o = c.options;
		boolean fwd = remaining < 45f;
		o.keyUp.setDown(fwd);
		o.keySprint.setDown(sprint && fwd);
		boolean higher = target.getY() > p.getBlockY();
		boolean jump = false;
		if (p.onGround() && fwd && (higher || p.horizontalCollision)) jump = true;
		if (p.isInWater() && target.getY() >= p.getBlockY()) jump = true;
		o.keyJump.setDown(jump);
	}
}

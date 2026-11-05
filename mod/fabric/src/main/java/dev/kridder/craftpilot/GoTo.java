package dev.kridder.craftpilot;

import java.util.List;

import com.google.gson.JsonObject;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/** Walks the player along an A* path by driving the movement key bindings. Render thread only. */
public final class GoTo {
	private static final int MAX_REPATHS = 3;
	private static final int STUCK_TICKS = 40;
	private static final float MAX_TURN = 30f;

	private static boolean active = false;
	private static String result = "stopped";
	private static List<BlockPos> path = List.of();
	private static int idx = 0;
	private static BlockPos goal = BlockPos.ORIGIN;
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

	public static JsonObject start(MinecraftClient c, double x, double y, double z, int maxDist, boolean sprintOn) {
		JsonObject o = new JsonObject();
		if (c.world == null || c.player == null) {
			o.addProperty("ok", false);
			o.addProperty("error", "not in a world");
			return o;
		}
		if (c.currentScreen != null) {
			o.addProperty("ok", false);
			o.addProperty("error", "a screen is open");
			return o;
		}
		if (active) stop(c, "stopped");
		goal = BlockPos.ofFloored(x, y, z);
		maxDistance = Math.max(1, Math.min(128, maxDist));
		sprint = sprintOn;
		repaths = 0;
		List<BlockPos> p = Pathfinder.find(c.world, c.player.getBlockPos(), goal, maxDistance);
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

	public static JsonObject status(MinecraftClient c) {
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

	public static JsonObject stopCmd(MinecraftClient c) {
		boolean was = active;
		if (active) stop(c, "stopped");
		JsonObject o = new JsonObject();
		o.addProperty("ok", true);
		o.addProperty("wasActive", was);
		return o;
	}

	private static void releaseKeys(MinecraftClient c) {
		if (c.options == null) return;
		GameOptions o = c.options;
		o.forwardKey.setPressed(false);
		o.sprintKey.setPressed(false);
		o.jumpKey.setPressed(false);
	}

	private static void stop(MinecraftClient c, String res) {
		active = false;
		result = res;
		path = List.of();
		releaseKeys(c);
	}

	public static void tick(MinecraftClient c) {
		if (!active) return;
		ClientPlayerEntity p = c.player;
		if (c.world == null || p == null) {
			stop(c, "failed:not in a world");
			return;
		}
		if (p.isDead() || p.getHealth() <= 0f) {
			stop(c, "failed:player died");
			return;
		}
		if (c.currentScreen != null) {
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
			List<BlockPos> np = Pathfinder.find(c.world, p.getBlockPos(), goal, maxDistance);
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
		float want = (float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
		float diff = MathHelper.wrapDegrees(want - p.getYaw());
		float step = MathHelper.clamp(diff, -MAX_TURN, MAX_TURN);
		float yaw = p.getYaw() + step;
		p.setYaw(yaw);
		p.setHeadYaw(yaw);
		p.setBodyYaw(yaw);
		float remaining = Math.abs(diff - step);

		GameOptions o = c.options;
		boolean fwd = remaining < 45f;
		o.forwardKey.setPressed(fwd);
		o.sprintKey.setPressed(sprint && fwd);
		boolean higher = target.getY() > p.getBlockY();
		boolean jump = false;
		if (p.isOnGround() && fwd && (higher || p.horizontalCollision)) jump = true;
		if (p.isTouchingWater() && target.getY() >= p.getBlockY()) jump = true;
		o.jumpKey.setPressed(jump);
	}
}

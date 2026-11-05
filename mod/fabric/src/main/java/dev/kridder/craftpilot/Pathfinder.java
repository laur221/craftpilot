package dev.kridder.craftpilot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

/** Simple client-side A* over block positions (loaded chunks only). Render thread only. */
public final class Pathfinder {
	private static final int PASS = 1;
	private static final int STAND = 2;
	private static final int WATER = 4;
	private static final int CLIMB = 8;
	private static final int NODE_LIMIT = 60000;

	private final ClientWorld w;
	private final Map<Long, Integer> cache = new HashMap<>();

	private Pathfinder(ClientWorld w) {
		this.w = w;
	}

	private static final class Node {
		final int x, y, z;
		double g, f;
		Node parent;
		boolean closed;

		Node(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	private int flags(int x, int y, int z) {
		long key = BlockPos.asLong(x, y, z);
		Integer c = cache.get(key);
		if (c != null) return c;
		int f = compute(x, y, z);
		cache.put(key, f);
		return f;
	}

	private int compute(int x, int y, int z) {
		if (y < w.getBottomY() || y >= w.getTopY()) return 0;
		if (!w.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return 0;
		BlockPos pos = new BlockPos(x, y, z);
		BlockState st = w.getBlockState(pos);
		Block b = st.getBlock();
		FluidState fs = st.getFluidState();
		boolean lava = fs.isIn(FluidTags.LAVA);
		boolean water = fs.isIn(FluidTags.WATER);
		boolean hazard = lava || b == Blocks.FIRE || b == Blocks.SOUL_FIRE || b == Blocks.CACTUS || b == Blocks.MAGMA_BLOCK
				|| b == Blocks.SWEET_BERRY_BUSH || b == Blocks.POWDER_SNOW || b == Blocks.WITHER_ROSE || b == Blocks.CAMPFIRE
				|| b == Blocks.SOUL_CAMPFIRE;
		VoxelShape shape = st.getCollisionShape(w, pos);
		boolean climb = st.isIn(BlockTags.CLIMBABLE);
		int f = 0;
		if (!hazard && (shape.isEmpty() || climb)) f |= PASS;
		if (water) f |= WATER;
		if (climb) f |= CLIMB;
		if (!hazard && !shape.isEmpty()) {
			double top = shape.getMax(Direction.Axis.Y);
			if (top >= 0.9 && top <= 1.0001) f |= STAND;
		}
		return f;
	}

	private boolean pass(int x, int y, int z) {
		return (flags(x, y, z) & PASS) != 0;
	}

	private boolean canStand(int x, int y, int z) {
		if (!pass(x, y, z) || !pass(x, y + 1, z)) return false;
		int here = flags(x, y, z);
		if ((here & (WATER | CLIMB)) != 0) return true;
		return (flags(x, y - 1, z) & STAND) != 0;
	}

	private double extra(int x, int y, int z) {
		return (flags(x, y, z) & WATER) != 0 ? 0.5 : 0.0;
	}

	/** Returns the waypoints after the start (feet block positions) or null if there is no path. */
	public static List<BlockPos> find(ClientWorld w, BlockPos start, BlockPos goal, int maxDistance) {
		Pathfinder p = new Pathfinder(w);
		BlockPos s = p.snap(start, 1, 2);
		BlockPos g = p.snap(goal, 4, 6);
		if (s == null) s = start;
		if (g == null) g = goal;
		List<BlockPos> r = p.search(s, g, maxDistance);
		System.out.println("[CraftPilot][goto] start=" + start.toShortString() + "->" + s.toShortString() + " goal=" + goal.toShortString() + "->" + g.toShortString()
				+ " expanded=" + p.lastExpanded + " reason=" + p.lastReason + " len=" + (r == null ? -1 : r.size()));
		return r;
	}

	private int lastExpanded;
	private String lastReason = "";

	/** Finds the first standable feet position in the column from y+up down to y-down. */
	private BlockPos snap(BlockPos p, int up, int down) {
		for (int dy = up; dy >= -down; dy--) {
			if (canStand(p.getX(), p.getY() + dy, p.getZ())) return new BlockPos(p.getX(), p.getY() + dy, p.getZ());
		}
		return null;
	}

	private static boolean isGoal(int x, int y, int z, BlockPos g) {
		return x == g.getX() && z == g.getZ() && Math.abs(y - g.getY()) <= 1;
	}

	private static double heuristic(int x, int y, int z, BlockPos g) {
		double dx = Math.abs(x - g.getX());
		double dz = Math.abs(z - g.getZ());
		double mn = Math.min(dx, dz), mx = Math.max(dx, dz);
		return 1.3 * ((mx - mn) + 1.41421356 * mn + Math.abs(y - g.getY()));
	}

	private List<BlockPos> search(BlockPos start, BlockPos goal, int maxDistance) {
		if (isGoal(start.getX(), start.getY(), start.getZ(), goal)) return new ArrayList<>();
		long maxSq = (long) maxDistance * maxDistance;
		Map<Long, Node> all = new HashMap<>();
		PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
		Node s = new Node(start.getX(), start.getY(), start.getZ());
		s.g = 0;
		s.f = heuristic(s.x, s.y, s.z, goal);
		all.put(BlockPos.asLong(s.x, s.y, s.z), s);
		open.add(s);
		int expanded = 0;
		int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
		while (!open.isEmpty() && expanded < NODE_LIMIT) {
			Node n = open.poll();
			if (n.closed) continue;
			n.closed = true;
			expanded++;
			if (isGoal(n.x, n.y, n.z, goal)) { lastExpanded = expanded; lastReason = "ok"; return build(n); }
			for (int[] d : dirs) {
				int dx = d[0], dz = d[1];
				int nx = n.x + dx, nz = n.z + dz;
				boolean diag = dx != 0 && dz != 0;
				long ddx = nx - start.getX(), ddz = nz - start.getZ();
				if (ddx * ddx + ddz * ddz > maxSq) continue;
				if (diag) {
					if (!pass(n.x + dx, n.y, n.z) || !pass(n.x + dx, n.y + 1, n.z)
							|| !pass(n.x, n.y, n.z + dz) || !pass(n.x, n.y + 1, n.z + dz)) continue;
					if (canStand(nx, n.y, nz)) relax(all, open, n, nx, n.y, nz, 1.41421356 + extra(nx, n.y, nz), goal);
					continue;
				}
				// flat
				if (canStand(nx, n.y, nz)) {
					relax(all, open, n, nx, n.y, nz, 1.0 + extra(nx, n.y, nz), goal);
					continue;
				}
				// step up 1 (needs 3 blocks of headroom above the current feet block)
				if (pass(n.x, n.y + 2, n.z) && canStand(nx, n.y + 1, nz) && (flags(nx, n.y, nz) & PASS) == 0) {
					relax(all, open, n, nx, n.y + 1, nz, 1.5 + extra(nx, n.y + 1, nz), goal);
					continue;
				}
				// drop up to 3 blocks
				if (pass(nx, n.y, nz) && pass(nx, n.y + 1, nz)) {
					for (int k = 1; k <= 3; k++) {
						int ny = n.y - k;
						if (!pass(nx, ny, nz)) break;
						if (canStand(nx, ny, nz)) {
							relax(all, open, n, nx, ny, nz, 1.0 + 0.5 * k + extra(nx, ny, nz), goal);
							break;
						}
					}
				}
			}
		}
		lastExpanded = expanded;
		lastReason = open.isEmpty() ? "exhausted" : "node limit";
		return null;
	}

	private void relax(Map<Long, Node> all, PriorityQueue<Node> open, Node from, int x, int y, int z, double cost, BlockPos goal) {
		long key = BlockPos.asLong(x, y, z);
		Node ex = all.get(key);
		double g = from.g + cost;
		if (ex != null) {
			if (ex.closed || g >= ex.g) return;
			ex.closed = true; // stale queue entry; a fresh node replaces it (keeps the heap consistent)
		}
		Node nn = new Node(x, y, z);
		nn.g = g;
		nn.f = g + heuristic(x, y, z, goal);
		nn.parent = from;
		all.put(key, nn);
		open.add(nn);
	}

	private static List<BlockPos> build(Node end) {
		List<BlockPos> out = new ArrayList<>();
		for (Node n = end; n != null && n.parent != null; n = n.parent) out.add(new BlockPos(n.x, n.y, n.z));
		Collections.reverse(out);
		return out;
	}
}

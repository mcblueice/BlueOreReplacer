package net.mcblueice.blueorereplacer.utils;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import net.mcblueice.blueorereplacer.BlueOreReplacer;
import net.mcblueice.blueorereplacer.utils.GenericUtil.BiomeMode;
import net.mcblueice.blueorereplacer.utils.GenericUtil.OreSelection;
import net.mcblueice.blueorereplacer.utils.GenericUtil.OreType;

public class OreReplaceUtil {

	private static final Set<Material> forbiddenSurfaces = EnumSet.of(
			Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
			Material.WATER, Material.LAVA,
			Material.BUBBLE_COLUMN
	);
	private static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

	private static final ChunkModificationTracker tracker = plugin().getChunkTracker();
	
	private static BlueOreReplacer plugin() { return BlueOreReplacer.getInstance(); }

	private static volatile Set<Material> undergroundSetCache;

	private static final Set<Material> STONE_CANDIDATES = EnumSet.of(
			//overworld
			Material.STONE, Material.DEEPSLATE, Material.TUFF,
			Material.GRANITE, Material.DIORITE, Material.ANDESITE,
			Material.GRAVEL, Material.CLAY, Material.DIRT,
			//nether
			Material.NETHERRACK, Material.BASALT, Material.SMOOTH_BASALT,
			Material.BLACKSTONE
	);

	public static void tryReplaceNeighbors(Block centerChanged) {
		if (centerChanged == null) return;
		if (tracker.isModified(centerChanged)) {
			plugin().sendDebug("§c跳過人工(自身)");
			return;
		}
		tracker.markModified(centerChanged);
		for (BlockFace face : FACES) {
			Block block = centerChanged.getRelative(face);
			if (!isUnderground(block.getType())) {
				plugin().sendDebug("§c跳過非地: " + GenericUtil.FaceToChinese(face) + " " + block.getType().name());
				continue;
			}
			if (tracker.isModified(block)) {
				plugin().sendDebug("§c跳過人工: " + GenericUtil.FaceToChinese(face) + " " + block.getType().name());
				continue;
			}
			plugin().sendDebug("§a嘗試替換: §7"+ GenericUtil.FaceToChinese(face) + " " + block.getType().name());
			tryReplace(block, centerChanged, false);
		}
		hideNearbyOres(centerChanged, plugin().getConfig().getInt("NearbyOreCheckRadius", 2));
	}

	public static void hideNearbyOres(Block center, int radius) {
		if (center == null || radius <= 0) return;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) continue;
					Block block = center.getRelative(dx, dy, dz);
					Material type = block.getType();
					if (isOre(type)) {
						plugin().sendDebug("§a周邊礦物隱藏: §7" + type.name() + " @ d=("+dx+","+dy+","+dz+")");
						hideOres(block);
						return;
					}
				}
			}
		}
	}

	public static void tryReplace(Block target, Block exclude, boolean ignoreNearby) {
		Environment env = target.getWorld().getEnvironment();
		if (env != Environment.NORMAL && env != Environment.NETHER) {
			plugin().sendDebug("  §4錯誤世界 無法生成");
			return;
		}
		if (tracker.isModified(target)) {
			plugin().sendDebug("  §4自身人工方塊 無法生成");
			return;
		}
		if (!isUnderground(target.getType())) {
			plugin().sendDebug("  §4自身非地方塊 無法生成");
			return;
		}
		if (!ignoreNearby) {
			for (BlockFace face : FACES) {
				Block block = target.getRelative(face);
				if (block.equals(exclude)) continue;
				if (forbiddenSurfaces.contains(block.getType())) {
					plugin().sendDebug("  §4周圍暴露方塊 無法生成");
					return;
				}
				if (tracker.isModified(block)) {
					plugin().sendDebug("  §4周圍人工方塊 無法生成");
					return;
				}
				if (!isUnderground(block.getType())) {
					plugin().sendDebug("  §4周圍非地方塊 無法生成");
					return;
				}
			}
		}

		Location loc = target.getLocation();
		OreSelection selection = OreSimulateUtil.getMostLikelyOre(target);

		if (selection == null) {
			if (hideOres(target)) return;
			plugin().sendDebug("  §7未骰中任何礦石");
			return;
		}

		OreType selectedType = selection.oreType();
		String oreName = GenericUtil.getOreName(selectedType, loc.getBlockY());
		Material result = Material.matchMaterial(oreName);

		String featureInfo = selection.featureName() != null ? (" §8[" + selection.featureName() + " size=" + selection.veinSize() + "]") : "";
		plugin().sendDebug("  §6替換礦石: §7" + result.name() + featureInfo);
		target.setType(result, false);
		tracker.markModified(target);
		int veinSize = Math.max(1, selection.veinSize());
		if (selectedType == OreType.COPPER_ORE) {
			BiomeMode biomeMode = GenericUtil.getBiomeMode(loc);
			if (biomeMode == BiomeMode.DRIPSTONE || biomeMode == BiomeMode.MOUNTAIN_DRIPSTONE) {
				veinSize *= 2;
			}
		}
		tryReplaceVein(target, exclude, veinSize);
	}

	public static void tryReplaceVein(Block target, Block originBlock, int veinSize) {
		Material startMat = target.getType();
		if (!isOre(startMat)) return;
		if (veinSize <= 0) return;

		int size = veinSize;
		int maxExtraBlocks = Math.max(0, veinSize - 1);

		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		World world = target.getWorld();
		double baseX = target.getX();
		double baseY = target.getY();
		double baseZ = target.getZ();

		boolean restrictAngle = false;
		double avoidAngle = 0.0D;
		final double exclusionHalfAngle = Math.toRadians(60.0D);
		if (originBlock != null) {
			double targetCenterX = baseX + 0.5D;
			double targetCenterZ = baseZ + 0.5D;
			double originalCenterX = originBlock.getX() + 0.5D;
			double originalCenterZ = originBlock.getZ() + 0.5D;
			double vecX = originalCenterX - targetCenterX;
			double vecZ = originalCenterZ - targetCenterZ;
			double lenSq = vecX * vecX + vecZ * vecZ;
			if (lenSq > 1.0E-6) {
				restrictAngle = true;
				avoidAngle = Math.atan2(vecX, vecZ);
				if (!Double.isFinite(avoidAngle)) {
					restrictAngle = false;
				}
			}
		}

		double angle = 0;
		double dirX = 0.0D;
		double dirZ = 0.0D;
		double startY = baseY + rnd.nextInt(3) - 1;
		double endY = baseY + rnd.nextInt(3) - 1;

		int maxDirectionAttempts = 5; // 重試次數
		int checkDist = Math.max(3, size / 4);
		boolean foundGoodDirection = false;

		for (int attempt = 0; attempt < maxDirectionAttempts; attempt++) {
			angle = AngleExcluding(rnd, restrictAngle, avoidAngle, exclusionHalfAngle);
			dirX = Math.sin(angle);
			dirZ = Math.cos(angle);
			boolean directionBlocked = false;

			for (int s = 1; s <= checkDist; s++) {
				int cx = (int) Math.round(baseX + dirX * s);
				int cz = (int) Math.round(baseZ + dirZ * s);
				for (int yOff = -1; yOff <= 1; yOff++) {
					int cy = (int) Math.round(baseY) + yOff;
					Block checkBlock = world.getBlockAt(cx, cy, cz);
					if (originBlock != null && checkBlock.equals(originBlock)) continue;
					Material m = checkBlock.getType();
					if (tracker.isModified(checkBlock) || forbiddenSurfaces.contains(m) || !isUnderground(m)) {
						plugin().sendDebug("  §e方向檢查: 嘗試 " + (attempt+1) + " 被阻擋 (距離=" + s + ") 發現 " + m.name());
						directionBlocked = true;
						break;
					}
				}
				if (directionBlocked) break;
			}

			if (!directionBlocked) { foundGoodDirection = true; break; }
		}

		if (!foundGoodDirection) plugin().sendDebug("  §6無法找到合適方向生成 使用最後方向生成礦脈");

		double spread = size / 8.0D;
		double startX = baseX + dirX * spread;
		double endX = baseX - dirX * spread;
		double startZ = baseZ + dirZ * spread;
		double endZ = baseZ - dirZ * spread;

		int replaced = 0;
		Set<Block> replacedBlocks = new HashSet<>();
		replacedBlocks.add(target);

		for (int i = 0; i < size && replaced < maxExtraBlocks; i++) {
			double t = (double) i / (double) size;
			double cx = startX + (endX - startX) * t;
			double cy = startY + (endY - startY) * t;
			double cz = startZ + (endZ - startZ) * t;

			double randomRadius = rnd.nextDouble() * size / 16.0;
			double radius = ((Math.sin(Math.PI * t) + 1.0) * randomRadius + 1.0) / 2.0;
			if (radius <= 0.0) {
				plugin().sendDebug("  §8礦脈步驟 " + i + " 半徑<=0.0 (radius=" + radius + ") 跳過");
				continue;
			}

			double halfX = radius;
			double halfY = radius;
			double halfZ = radius;

			int minX = (int) Math.floor(cx - halfX);
			int maxX = (int) Math.floor(cx + halfX);
			int minY = (int) Math.floor(cy - halfY);
			int maxY = (int) Math.floor(cy + halfY);
			int minZ = (int) Math.floor(cz - halfZ);
			int maxZ = (int) Math.floor(cz + halfZ);

			for (int x = minX; x <= maxX && replaced < maxExtraBlocks; x++) {
				for (int y = minY; y <= maxY && replaced < maxExtraBlocks; y++) {
					for (int z = minZ; z <= maxZ && replaced < maxExtraBlocks; z++) {
						double dxn = (x + 0.5 - cx) / halfX;
						double dyn = (y + 0.5 - cy) / halfY;
						double dzn = (z + 0.5 - cz) / halfZ;
						if (dxn * dxn + dyn * dyn + dzn * dzn >= 1.0) continue;

						Block b = world.getBlockAt(x, y, z);
						Material mat = b.getType();
						boolean isOrigin = originBlock != null && b.equals(originBlock);
						boolean isTarget = b.equals(target);
						if (isTarget) {
							logVeinResult(x, y, z, false, "中心方塊已處理");
							continue;
						}

						if (!isOrigin && forbiddenSurfaces.contains(mat)) {
							logVeinResult(x, y, z, false, "目標方塊為暴露方塊: " + mat.name());
							continue;
						}
						if (!isOrigin && !isUnderground(mat)) {
							logVeinResult(x, y, z, false, "目標方塊非地方塊: " + mat.name());
							continue;
						}
						if (!isOrigin && tracker.isModified(b)) {
							logVeinResult(x, y, z, false, "目標方塊被標記為人工");
							continue;
						}

						boolean unsafe = false;
						String unsafeReason = null;
						for (BlockFace face : FACES) {
							Block around = b.getRelative(face);
							if (originBlock != null && around.equals(originBlock)) continue;
							if (around.equals(target)) continue;
							Material aroundMat = around.getType();
							if (forbiddenSurfaces.contains(aroundMat)) { unsafe = true; unsafeReason = "鄰近存在暴露方塊 " + aroundMat.name(); break; }
							if (!isUnderground(aroundMat)) { unsafe = true; unsafeReason = "鄰近存在非地方塊 " + aroundMat.name(); break; }
							if (tracker.isModified(around) && !replacedBlocks.contains(around)) { unsafe = true; unsafeReason = "鄰近存在人工方塊"; break; }
						}
						if (unsafe) {
							logVeinResult(x, y, z, false, unsafeReason);
							continue;
						}

						b.setType(startMat, false);
						tracker.markModified(b);
						replacedBlocks.add(b);
						replaced++;
						logVeinResult(x, y, z, true, null);
					}
				}
			}
		}

		int totalPlaced = Math.min(veinSize, replaced + 1);
		plugin().sendDebug("  §d礦脈生長完成: size=" + totalPlaced + "/scale=" + size + "");
	}

	private static double AngleExcluding(ThreadLocalRandom rnd, boolean restrain, double avoid, double exclusionHalfAngle) {
		double fullCircle = Math.PI * 2.0D;
		if (!restrain) {
			return rnd.nextDouble() * fullCircle;
		}
		for (int attempts = 0; attempts < 64; attempts++) {
			double candidate = rnd.nextDouble() * fullCircle;
			double diff = Math.atan2(Math.sin(candidate - avoid), Math.cos(candidate - avoid));
			if (!Double.isFinite(diff) || Math.abs(diff) > exclusionHalfAngle) {
				return candidate;
			}
		}
		return rnd.nextDouble() * fullCircle;
	}

	private static void logVeinResult(int x, int y, int z, boolean success, String reason) {
		String status = success ? "§a成功" : "§c取消";
		String reasonText = (reason == null || reason.isEmpty()) ? "" : " §7(" + reason + ")";
		plugin().sendDebug("  §8礦脈[" + x + "," + y + "," + z + "] " + status + reasonText);
	}

	public static boolean hideOres(Block target) {
		if (!isOre(target.getType())) return false;
		if (tracker.isModified(target)) {
			plugin().sendDebug("  §4自身人工方塊 無法隱藏");
			return false;
		}
		if (!isUnderground(target.getType())) {
			plugin().sendDebug("  §4自身非地方塊 無法隱藏");
			return false;
		}

		Material startMat = target.getType();
		Deque<Block> queue = new ArrayDeque<>();
		Set<Block> vein = new HashSet<>();
		queue.add(target);
		vein.add(target);

		int maxVeinBlocks = 32;
		while (!queue.isEmpty() && vein.size() < maxVeinBlocks) {
			Block cur = queue.poll();
			for (BlockFace face : FACES) {
				Block newBlock = cur.getRelative(face);
				if (vein.contains(newBlock)) continue;
				Material newBlockMat = newBlock.getType();
				if (isOre(newBlockMat) && sameOreFamily(startMat, newBlockMat)) {
					vein.add(newBlock);
					queue.add(newBlock);
				}
			}
		}

		for (Block ore : vein) {
			for (BlockFace face : FACES) {
				Block around = ore.getRelative(face);
				if (vein.contains(around)) continue;
				Material mat = around.getType();
				if (forbiddenSurfaces.contains(mat)) {
					plugin().sendDebug("  §4礦簇周圍暴露方塊 無法隱藏");
					return false;
				}
				if (tracker.isModified(around)) {
					plugin().sendDebug("  §4礦簇周圍人工方塊 無法隱藏");
					return false;
				}
				if (!isUnderground(mat)) {
					plugin().sendDebug("  §4礦簇周圍非地方塊 無法隱藏");
					return false;
				}
			}
		}
		Map<Material, Integer> count = new HashMap<>();
		for (BlockFace face : FACES) {
			Material newTarget = target.getRelative(face).getType();
			if (STONE_CANDIDATES.contains(newTarget)) {
				count.put(newTarget, count.getOrDefault(newTarget, 0) + 1);
			}
		}
		Material fallback = null;
		int best = 0;
		for (Map.Entry<Material, Integer> e : count.entrySet()) {
			if (e.getValue() > best) { best = e.getValue(); fallback = e.getKey(); }
		}
		if (fallback == null) {
			switch (target.getWorld().getEnvironment()) {
			case NETHER:
				fallback = Material.NETHERRACK;
				break;
			case NORMAL:
				if (target.getY() < 0) {
					fallback = Material.DEEPSLATE;
				} else {
					fallback = Material.STONE;
				}
				break;
			default:
				fallback = Material.STONE;
				break;
			}
		}
		plugin().sendDebug("  §b整脈隱藏為: §7" + fallback.name());
		replaceConnectedVein(target, fallback, true);
		return true;
	}

	private static boolean isUnderground(Material material) {
        Set<Material> cache = undergroundSetCache;
        if (cache == null) {
			List<String> list = plugin().getConfig().getStringList("UndergroundBlocks");
            EnumSet<Material> set = EnumSet.noneOf(Material.class);
            for (String str : list) {
                Material mat = Material.matchMaterial(str);
                if (mat != null) set.add(mat);
            }
            cache = Collections.unmodifiableSet(set);
            undergroundSetCache = cache;
        }
        return cache.contains(material);
    }

	private static boolean isOre(Material m) {
		return m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS;
	}


	private static boolean sameOreFamily(Material a, Material b) {
		OreType ta = GenericUtil.stringToOreType(a.name());
		OreType tb = GenericUtil.stringToOreType(b.name());
		if (ta != null && tb != null) return ta == tb;
		return a == b;
	}

	private static void replaceConnectedVein(Block start, Material replacement, boolean onlyNatural) {
		Material startMat = start.getType();
		if (!isOre(startMat)) { start.setType(replacement, false); return; }

		Deque<Block> queue = new ArrayDeque<>();
		Set<Block> visited = new HashSet<>();
		queue.add(start);
		visited.add(start);
		int replaced = 0;

		int maxVeinBlocks = 32;
		while (!queue.isEmpty() && replaced < maxVeinBlocks) {
			Block cur = queue.poll();
			Material curMat = cur.getType();
			if (!isOre(curMat) || !sameOreFamily(startMat, curMat)) continue;
			if (onlyNatural && cur != start && tracker != null && tracker.isModified(cur)) continue;

			cur.setType(replacement, false);
			replaced++;

			for (BlockFace face : FACES) {
				Block newBlock = cur.getRelative(face);
				if (!visited.contains(newBlock)) {
					visited.add(newBlock);
					Material newBlockMat = newBlock.getType();
					if (isOre(newBlockMat) && sameOreFamily(startMat, newBlockMat)) {
						queue.add(newBlock);
					}
				}
			}
		}
		plugin().sendDebug("  §7整脈替換完成 共 " + replaced + " 格");
	}
}
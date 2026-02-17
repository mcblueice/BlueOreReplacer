package net.mcblueice.blueorereplacer.utils;

import java.util.*;

import org.bukkit.Location;
import org.bukkit.Material;
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

	private static volatile ChunkModificationTracker tracker;

	private static volatile int nearbyOreCheckRadius = 2;
	private static volatile Set<Material> undergroundSet = Collections.emptySet();

	private static final Set<Material> STONE_CANDIDATES = EnumSet.of(
			//overworld
			Material.STONE, Material.DEEPSLATE, Material.TUFF,
			Material.GRANITE, Material.DIORITE, Material.ANDESITE,
			Material.GRAVEL, Material.CLAY, Material.DIRT,
			//nether
			Material.NETHERRACK, Material.BASALT, Material.SMOOTH_BASALT,
			Material.BLACKSTONE
	);

	public static void reload() {
		BlueOreReplacer plugin = BlueOreReplacer.getInstance();
		tracker = plugin.getChunkTracker();
		nearbyOreCheckRadius = plugin.getConfig().getInt("NearbyOreCheckRadius", 2);
		BlueOreReplacer.sendMessage("§7鄰近礦物檢查半徑已設置為 §e" + nearbyOreCheckRadius);

		List<String> undergroundBlocks = plugin.getConfig().getStringList("UndergroundBlocks");
		EnumSet<Material> undergroundBlockSet = EnumSet.noneOf(Material.class);
		for (String str : undergroundBlocks) {
            Material mat = Material.matchMaterial(str);
            if (mat != null) undergroundBlockSet.add(mat);
        }
		undergroundSet = Collections.unmodifiableSet(undergroundBlockSet);
		BlueOreReplacer.sendMessage("§7地下方塊緩存已更新 共 §e" + undergroundBlockSet.size() + " §7種材質");
    }

	public static void tryReplaceNeighbors(Block centerChanged) {
		if (centerChanged == null) return;
		if (tracker.isModified(centerChanged)) {
			if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("§c跳過人工(自身)");
			return;
		}
		tracker.markModified(centerChanged);
		for (BlockFace face : FACES) {
			Block block = centerChanged.getRelative(face);
			if (!isUnderground(block.getType())) {
				if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("§c跳過非地: " + GenericUtil.FaceToChinese(face) + " " + block.getType().name());
				continue;
			}
			if (tracker.isExposed(block)) {
				if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("§c跳過暴露: " + GenericUtil.FaceToChinese(face) + " " + block.getType().name());
				continue;
			}
			if (tracker.isModified(block)) {
				if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("§c跳過人工: " + GenericUtil.FaceToChinese(face) + " " + block.getType().name());
				continue;
			}
			if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("§a嘗試替換: §7"+ GenericUtil.FaceToChinese(face) + " " + block.getType().name());
			tryReplace(block, centerChanged, false);
		}
		hideNearbyOres(centerChanged, nearbyOreCheckRadius);
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
						if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("§a周邊礦物隱藏: §7" + type.name() + " @ d=("+dx+","+dy+","+dz+")");
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
			if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("  §4錯誤世界 無法生成");
			return;
		}
		if (tracker.isModified(target)) {
			if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("  §4自身人工方塊 無法生成");
			return;
		}
		if (tracker.isExposed(target)) {
			if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("  §4自身暴露方塊 無法生成");
			return;
		}
		if (!isUnderground(target.getType())) {
			if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("  §4自身非地方塊 無法生成");
			return;
		}
		if (!ignoreNearby) {
			for (BlockFace face : FACES) {
				Block block = target.getRelative(face);
				if (block.equals(exclude)) continue;
				if (forbiddenSurfaces.contains(block.getType())) {
					if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("  §4周圍空氣方塊 無法生成");
					return;
				}
				if (tracker.isModified(block)) {
					if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("  §4周圍人工方塊 無法生成");
					return;
				}
				if (!isUnderground(block.getType())) {
					if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("  §4周圍非地方塊 無法生成");
					return;
				}
			}
		}

		tracker.markExposed(target);

		Location loc = target.getLocation();
		OreSelection selection = OreSimulateUtil.getMostLikelyOre(target);

		if (selection == null) {
			if (hideOres(target)) return;
			if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("  §7未骰中任何礦石");
			return;
		}

		OreType selectedType = selection.oreType();
		String oreName = GenericUtil.getOreName(selectedType, loc.getBlockY());
		Material result = Material.matchMaterial(oreName);

		String featureInfo = selection.featureName() != null ? (" §8[" + selection.featureName() + " size=" + selection.veinSize() + "]") : "";
		if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("  §6替換礦石: §7" + result.name() + featureInfo);
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
		VeinGenUtil.growVein(
				target,
				originBlock,
				veinSize,
				forbiddenSurfaces,
				FACES,
				tracker,
				OreReplaceUtil::isUnderground,
				OreReplaceUtil::isOre,
				BlueOreReplacer.debug
		);
	}

	public static boolean hideOres(Block target) {
		if (!isOre(target.getType())) return false;
		if (tracker.isModified(target)) {
			if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("  §4自身人工方塊 無法隱藏");
			return false;
		}
		if (!isUnderground(target.getType())) {
			if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("  §4自身非地方塊 無法隱藏");
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
					if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("  §4礦簇周圍暴露方塊 無法隱藏");
					return false;
				}
				if (tracker.isModified(around)) {
					if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("  §4礦簇周圍人工方塊 無法隱藏");
					return false;
				}
				if (!isUnderground(mat)) {
					if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("  §4礦簇周圍非地方塊 無法隱藏");
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
		if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("  §b整脈隱藏為: §7" + fallback.name());
		replaceConnectedVein(target, fallback, true);
		return true;
	}

	private static boolean isUnderground(Material material) {
        return undergroundSet.contains(material);
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
					if (isOre(newBlockMat) && sameOreFamily(startMat, newBlockMat)) queue.add(newBlock);
				}
			}
		}
		if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("  §7整脈替換完成 共 " + replaced + " 格");
	}
}
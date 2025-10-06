package net.mcblueice.blueorereplacer.utils;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

public final class GenericUtil {

    private GenericUtil() {}

    public enum OreType {
        // OVERWORLD
        COAL_ORE,
        IRON_ORE,
        COPPER_ORE,
        GOLD_ORE,
        REDSTONE_ORE,
        EMERALD_ORE,
        LAPIS_ORE,
        DIAMOND_ORE,
        // NETHER
        NETHER_GOLD_ORE,
        NETHER_QUARTZ_ORE,
        ANCIENT_DEBRIS
    }

    public enum BiomeMode {
        // OVERWORLD
        NORMAL,        // 一般
        BADLANDS,       // 惡地
        MOUNTAIN,       // 山地
        DRIPSTONE,      // 滴石
        MOUNTAIN_DRIPSTONE, // 山地+滴石
        // NETHER
        NETHER
    }

    public static String FaceToChinese(BlockFace face) {
        if (face == null) return "未知方向";
        switch (face) {
            case UP: return "上";
            case DOWN: return "下";

            case NORTH: return "北";
            case SOUTH: return "南";
            case EAST: return "東";
            case WEST: return "西";

            case NORTH_EAST: return "東北";
            case NORTH_WEST: return "西北";
            case SOUTH_EAST: return "東南";
            case SOUTH_WEST: return "西南";

            case WEST_NORTH_WEST: return "西偏北";
            case NORTH_NORTH_WEST: return "北偏西";
            case NORTH_NORTH_EAST: return "北偏東";
            case EAST_NORTH_EAST: return "東偏北";
            case EAST_SOUTH_EAST: return "東偏南";
            case SOUTH_SOUTH_EAST: return "南偏東";
            case SOUTH_SOUTH_WEST: return "南偏西";
            case WEST_SOUTH_WEST: return "西偏南";

            default: return "未知方向";
        }
    }

    public static String getOreName(OreType oreType, int y) {
        if (oreType == null) return "UNKNOWN";
        boolean deep = y < 0;
        switch (oreType) {
            case COAL_ORE: return deep ? "DEEPSLATE_COAL_ORE" : "COAL_ORE";
            case IRON_ORE: return deep ? "DEEPSLATE_IRON_ORE" : "IRON_ORE";
            case COPPER_ORE: return deep ? "DEEPSLATE_COPPER_ORE" : "COPPER_ORE";
            case GOLD_ORE: return deep ? "DEEPSLATE_GOLD_ORE" : "GOLD_ORE";
            case REDSTONE_ORE: return deep ? "DEEPSLATE_REDSTONE_ORE" : "REDSTONE_ORE";
            case EMERALD_ORE: return deep ? "DEEPSLATE_EMERALD_ORE" : "EMERALD_ORE";
            case LAPIS_ORE: return deep ? "DEEPSLATE_LAPIS_ORE" : "LAPIS_ORE";
            case DIAMOND_ORE: return deep ? "DEEPSLATE_DIAMOND_ORE" : "DIAMOND_ORE";
            case NETHER_GOLD_ORE: return "NETHER_GOLD_ORE";
            case NETHER_QUARTZ_ORE: return "NETHER_QUARTZ_ORE";
            case ANCIENT_DEBRIS: return "ANCIENT_DEBRIS";
            default: return oreType.name();
        }
    }

    public static String getOreName(String oreName, int y) {
        return getOreName(stringToOreType(oreName), y);
    }
    public static String getOreName(Material oreMaterial, int y) {
        return getOreName(stringToOreType(oreMaterial.name()), y);
    }

    public static OreType stringToOreType(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String name = raw.trim().toUpperCase();
        if (name.startsWith("MINECRAFT:")) name = name.substring("MINECRAFT:".length());
        if (name.startsWith("DEEPSLATE_")) {
            name = name.substring("DEEPSLATE_".length());
        }
        try { return OreType.valueOf(name); } catch (IllegalArgumentException ignored) {}
        if (!name.endsWith("_ORE")) {
            try { return OreType.valueOf(name + "_ORE"); } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

}

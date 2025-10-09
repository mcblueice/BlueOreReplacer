package net.mcblueice.blueorereplacer.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
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
        NETHER,
        // THE_END
        THE_END
    }

    public record OreSelection(OreType oreType, int veinSize, String featureName) {
        public OreSelection(OreType oreType, int veinSize) {
            this(oreType, veinSize, null);
        }
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
            default: return "未知方向";
        }
    }

    public static BiomeMode getBiomeMode(Location loc) {
        World world = loc.getWorld();
        if (world.getEnvironment() == World.Environment.NETHER) return BiomeMode.NETHER;
        if (world.getEnvironment() == World.Environment.THE_END) return BiomeMode.THE_END;
        Biome biome = world.getBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        String biomeName = biome.name().toLowerCase();
        
        if (biomeName.contains("mountain") || biomeName.contains("peaks") || biomeName.contains("slopes") || biomeName.contains("summit")) {
            if (biomeName.contains("dripstone")) { return BiomeMode.MOUNTAIN_DRIPSTONE; }
            return BiomeMode.MOUNTAIN;
        }
        if (biomeName.contains("badlands")) { return BiomeMode.BADLANDS; }
        if (biomeName.contains("dripstone")) { return BiomeMode.DRIPSTONE; }
        return BiomeMode.NORMAL;
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

package net.mcblueice.blueorereplacer.utils;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import net.mcblueice.blueorereplacer.BlueOreReplacer;
import net.mcblueice.blueorereplacer.utils.GenericUtil.BiomeMode;
import net.mcblueice.blueorereplacer.utils.GenericUtil.OreType;;

public class OreSimulateUtil {
    private static BlueOreReplacer plugin() { return BlueOreReplacer.getInstance(); }

    @SuppressWarnings("unused")
    private static class OreFeature {
        public final String name;
        public final OreType ore;
        public int veinSize;
        public final double count;
        public final int yMin;
        public final int yMax;
        public final String distType;
        public final double k;
        public final int hSpread;
        public final String biomeTag;
        public double multiplier;
        public final int dYMin;
        public final int dYMax;
        public final double[] weights;
        public final double activeWeightSum;

        public OreFeature(String name, OreType ore, int veinSize, double count,
                          int yMin, int yMax, // 生成範圍
                          int dYMin, int dYMax, // 計算範圍
                          String distType, double k,
                          int hSpread, String biomeTag, double multiplier) {
            this.name = name;
            this.ore = ore;
            this.veinSize = veinSize;
            this.count = count;
            this.yMin = yMin;
            this.yMax = yMax;
            this.dYMin = dYMin;
            this.dYMax = dYMax;
            this.distType = distType;
            this.k = k;
            this.hSpread = hSpread;
            this.biomeTag = biomeTag;
            this.multiplier = multiplier;

            if ("tri".equals(distType)) {
                this.weights = triangularDistribution(dYMin, dYMax);
            } else if ("uniform".equals(distType)) {
                this.weights = uniformDistribution(dYMin, dYMax);
            } else {
                throw new IllegalArgumentException("Unsupported distribution type: " + distType);
            }
            double sumActive = 0.0;
            int from = Math.max(yMin, dYMin);
            int to = Math.min(yMax, dYMax);
            for (int yy = from; yy <= to; yy++) {
                sumActive += this.weights[yy - dYMin];
            }
            this.activeWeightSum = (sumActive > 0) ? sumActive : 1.0;
        }

        public double pBlockLayer(int y) {
            if (y < yMin || y > yMax) return 0.0;
            if (y < dYMin || y > dYMax) return 0.0;
            double py = weights[y - dYMin] / activeWeightSum;
            return count * multiplier * py * (k * veinSize / hSpread) / 256.0;
        }
    }

    private static final List<OreFeature> BASE_FEATURE_SET = createBaseFeatureSet();

    private static List<OreFeature> createBaseFeatureSet() {
        List<OreFeature> features = new ArrayList<>();

        // 煤礦: 主分佈(三角) + 高海拔(平均)
        features.add(new OreFeature("coal_main", OreType.COAL_ORE, 17, 20, 0, 192, 0, 192, "tri", 0.9, 3, "generic", 1.0));
        features.add(new OreFeature("coal_alt", OreType.COAL_ORE, 17, 30, 136, 256, 136, 256, "uniform", 0.9, 3, "generic", 1.0));

        // 鐵礦: 主分佈(三角) + 高海拔(三角) + 平均分佈(平均)
        features.add(new OreFeature("iron_main", OreType.IRON_ORE, 9, 10, -24, 56, -24, 56, "tri", 0.9, 3, "generic", 1.0));
        features.add(new OreFeature("iron_high", OreType.IRON_ORE, 9, 90, 80, 256, 80, 384, "tri", 0.9, 3, "generic", 1.0));
        features.add(new OreFeature("iron_alt", OreType.IRON_ORE, 9, 10, -64, 72, -64, 72, "uniform", 0.9, 3, "generic", 1.0));

        // 銅礦: 主分佈(三角) + 滴洞覆蓋(三角)
        features.add(new OreFeature("copper_main", OreType.COPPER_ORE, 10, 16, -16, 112, -16, 112, "tri", 0.9, 3, "generic", 1.0));
        // features.add(new OreFeature("copper_dripstone", OreType.COPPER_ORE, 20, 16, -16, 112, -16, 112, "tri", 0.9, 3, "dripstone", 1.0));

        // 金礦: 主分佈(三角) + 惡地附加(平均)
        features.add(new OreFeature("gold_main", OreType.GOLD_ORE, 9, 4, -64, 32, -64, 32, "tri", 0.9, 3, "generic", 1.0));
        features.add(new OreFeature("gold_alt", OreType.GOLD_ORE, 9, 2, -64, -48, -64, -48, "uniform", 0.9, 3, "generic", 1.0));
        features.add(new OreFeature("gold_badlands_extra", OreType.GOLD_ORE, 9, 50, 32, 256, 32, 256, "uniform", 0.9, 3, "badlands", 1.0));

        // 青金石: 主分佈(三角) + 平均分佈(平均)
        features.add(new OreFeature("lapis_main", OreType.LAPIS_ORE, 6, 2, -32, 32, -32, 32, "tri", 0.9, 3, "generic", 1.0));
        features.add(new OreFeature("lapis_alt", OreType.LAPIS_ORE, 6, 4, -64, 64, -64, 64, "uniform", 0.9, 3, "generic", 1.0));

        // 紅石: 主分佈(三角) + 平均分佈(平均)
        features.add(new OreFeature("redstone_main", OreType.REDSTONE_ORE, 8, 8, -64, -32, -96, -32, "tri", 0.9, 3, "generic", 1.0));
        features.add(new OreFeature("redstone_alt", OreType.REDSTONE_ORE, 8, 4, -64, 16, -64, 16, "uniform", 0.9, 3, "generic", 1.0));

        // 綠寶石: 主分佈(三角)
        features.add(new OreFeature("emerald_mountain", OreType.EMERALD_ORE, 3, 100, -16, 256, -16, 480, "tri", 0.9, 3, "mountain", 1.0));

        // 鑽石: 主分佈(三角)
        features.add(new OreFeature("diamond_main", OreType.DIAMOND_ORE, 8, 8, -64, 16, -144, 16, "tri", 0.9, 3, "generic", 1.0));

        // 石英礦: 主分佈(平均)
        features.add(new OreFeature("nether_quartz_main", OreType.NETHER_QUARTZ_ORE, 14, 16, 10, 117, 10, 117, "uniform", 0.9, 3, "nether", 1.0));
        // 地獄金礦: 主分佈(平均)
        features.add(new OreFeature("nether_gold_main", OreType.NETHER_GOLD_ORE, 10, 10, 10, 117, 10, 117, "uniform", 0.9, 3, "nether", 1.0));
        // 遠古遺骸: 主分佈(三角) + 平均分佈(平均)
        features.add(new OreFeature("debris_main", OreType.ANCIENT_DEBRIS, 3, 1, 8, 24, 8, 24, "tri", 0.9, 3, "nether", 1.0));
        features.add(new OreFeature("debris_alt", OreType.ANCIENT_DEBRIS, 2, 1, 8, 119, 8, 119, "uniform", 0.9, 3, "nether", 1.0));

        return features;
    }

    private static int getConfiguredVeinSize(String worldName, OreType oreType, int y, int defaultSize) {
        String matName = GenericUtil.getOreName(oreType, y);
        String path = "OresGeneration." + worldName + "." + matName + ".maxsize";
        Object raw = plugin().getConfig().get(path);
        if (raw instanceof Number n) {
            int v = n.intValue();
            if (v > 0) return v;
        }
        return defaultSize;
    }

    private static double[] triangularDistribution(int a, int b) {
        int n = b - a;
        double[] weights = new double[n + 1];
        double total = 0;
        
        for (int k = 0; k <= n; k++) {
            if (k <= n / 2.0) {
                weights[k] = k + 1;
            } else {
                weights[k] = n - k + 1;
            }
            total += weights[k];
        }
        
        for (int i = 0; i < weights.length; i++) {
            weights[i] /= total;
        }
        
        return weights;
    }

    private static double[] uniformDistribution(int a, int b) {
        int span = b - a + 1;
        double[] weights = new double[span];
        double value = 1.0 / span;
        Arrays.fill(weights, value);
        return weights;
    }

    private static List<OreFeature> BiomeModifiers(List<OreFeature> features, BiomeMode biomeMode) {
        List<OreFeature> result = new ArrayList<>();
        for (OreFeature f : features) {
            boolean matches = false;
            switch (f.biomeTag) {
                case "generic" -> matches = biomeMode != BiomeMode.NETHER;
                case "nether" -> matches = biomeMode == BiomeMode.NETHER;
                case "badlands" -> matches = biomeMode == BiomeMode.BADLANDS;
                case "mountain" -> matches = biomeMode == BiomeMode.MOUNTAIN || biomeMode == BiomeMode.MOUNTAIN_DRIPSTONE;
                default -> {}
            }
            if (matches) result.add(f);
        }
        return result;
    }

    public static BiomeMode getBiomeModeFromLocation(Location loc) {
        if (loc.getWorld().getEnvironment() == World.Environment.NETHER) return BiomeMode.NETHER;
        Biome biome = loc.getWorld().getBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        String biomeName = biome.name().toLowerCase();
        
        if (biomeName.contains("badlands")) {
            return BiomeMode.BADLANDS;
        } else if (biomeName.contains("dripstone")) {
            return BiomeMode.DRIPSTONE;
        } else if (biomeName.contains("mountain") || biomeName.contains("peaks") || 
                   biomeName.contains("slopes") || biomeName.contains("summit")) {
            if (biomeName.contains("dripstone")) {
                return BiomeMode.MOUNTAIN_DRIPSTONE;
            }
            return BiomeMode.MOUNTAIN;
        }
        
        return BiomeMode.NORMAL;
    }

    public static Double calculateOreProbability(Location loc, OreType oreType) {
        int y = loc.getBlockY();
        BiomeMode biomeMode = getBiomeModeFromLocation(loc);
        
        List<OreFeature> features = BiomeModifiers(BASE_FEATURE_SET, biomeMode);
        List<OreFeature> oreFeatures = new ArrayList<>();
        
        for (OreFeature f : features) {
            if (f.ore.equals(oreType)) {
                oreFeatures.add(f);
            }
        }
        
        if (oreFeatures.isEmpty()) {
            return null;
        }
        
        double combinedProbability = 0;
        String worldNameCalc = loc.getWorld().getName();
        for (OreFeature f : oreFeatures) {
            int configured = getConfiguredVeinSize(worldNameCalc, f.ore, y, f.veinSize);
            if (configured != f.veinSize) {
                f.veinSize = configured;
            }
            double p = f.pBlockLayer(y);
            combinedProbability = combinedProbability + p - (combinedProbability * p);
        }
        
        if (combinedProbability <= 0) return 0d;
        String worldName = loc.getWorld().getName();
        String base = "OresGeneration." + worldName + "." + GenericUtil.getOreName(oreType, y);
        boolean enabled = plugin().getConfig().getBoolean(base + ".enabled", true);
        double chance = 1.0;
        Object raw = plugin().getConfig().get(base + ".chance");
        if (raw instanceof Number num) {
            chance = num.doubleValue();
        }
        if (!enabled || chance <= 0) return 0d;
        return combinedProbability * chance;
    }

    public static Double calculateOreProbability(Location loc, OreType oreType, BiomeMode overrideBiome) {
        int y = loc.getBlockY();
        BiomeMode biomeMode = (overrideBiome != null) ? overrideBiome : getBiomeModeFromLocation(loc);

        List<OreFeature> features = BiomeModifiers(BASE_FEATURE_SET, biomeMode);
        List<OreFeature> oreFeatures = new ArrayList<>();

        for (OreFeature f : features) {
            if (f.ore.equals(oreType)) {
                oreFeatures.add(f);
            }
        }

        if (oreFeatures.isEmpty()) {
            return null;
        }

        double combinedProbability = 0;
        String worldNameCalc2 = loc.getWorld().getName();
        for (OreFeature f : oreFeatures) {
            int configured = getConfiguredVeinSize(worldNameCalc2, f.ore, y, f.veinSize);
            if (configured != f.veinSize) {
                f.veinSize = configured;
            }
            double p = f.pBlockLayer(y);
            combinedProbability = combinedProbability + p - (combinedProbability * p);
        }

        if (combinedProbability <= 0) return 0d;
        String worldName = loc.getWorld().getName();
        String base = "OresGeneration." + worldName + "." + GenericUtil.getOreName(oreType, y);
        boolean enabled = plugin().getConfig().getBoolean(base + ".enabled", true);
        double chance = 1.0;
        Object raw = plugin().getConfig().get(base + ".chance");
        if (raw instanceof Number num) {
            chance = num.doubleValue();
        }
        if (!enabled || chance <= 0) return 0d;
        return combinedProbability * chance;
    }

    public static OreType getMostLikelyOre(Block block) {
        Location loc = block.getLocation();
        Map<OreType, Double> weights = new EnumMap<>(OreType.class);
        double sum = 0.0;

        for (OreType ore : OreType.values()) {
            Double p = calculateOreProbability(loc, ore);
            if (p != null && p > 0) {
                weights.put(ore, p);
                sum += p;
            }
        }

        if (sum <= 0) return null;

        if (sum > 1.0) {
            double inv = 1.0 / sum;
            for (Map.Entry<OreType, Double> e : weights.entrySet()) {
                e.setValue(e.getValue() * inv);
            }
            sum = 1.0;
        }

        double r = ThreadLocalRandom.current().nextDouble();
        if (r >= sum) return null;

        double cumulative = 0.0;
        for (Map.Entry<OreType, Double> e : weights.entrySet()) {
            cumulative += e.getValue();
            if (r < cumulative) {
                if (e.getKey().name().startsWith("NETHER_") && !block.getType().equals(Material.NETHERRACK)) return null;
                return e.getKey();
            }
        }
        return null;
    }

    public static int getVeinSize(Location location, OreType oreType) {
        if (location == null || oreType == null) return 1;
        World world = location.getWorld();
        if (world == null) return 1;
        String worldName = world.getName();
        int y = location.getBlockY();
        int max = 1;
        for (OreFeature f : BASE_FEATURE_SET) {
            if (f.ore.equals(oreType)) {
                int configured = getConfiguredVeinSize(worldName, f.ore, y, f.veinSize);
                if (configured > max) max = configured;
            }
        }
        if (oreType == OreType.COPPER_ORE) {
            BiomeMode biomeMode = getBiomeModeFromLocation(location);
            if (biomeMode == BiomeMode.DRIPSTONE || biomeMode == BiomeMode.MOUNTAIN_DRIPSTONE) {
                max *= 2;
            }
        }
        return max;
    }

    public static int getVeinSize(Block block) {
        if (block == null) return 1;
        OreType oreType = GenericUtil.stringToOreType(block.getType().name());
        if (oreType == null) return 1;
        return getVeinSize(block.getLocation(), oreType);
    }
}
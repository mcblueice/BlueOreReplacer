package net.mcblueice.blueorereplacer.utils;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import net.mcblueice.blueorereplacer.utils.GenericUtil.BiomeMode;
import net.mcblueice.blueorereplacer.utils.GenericUtil.OreSelection;
import net.mcblueice.blueorereplacer.utils.GenericUtil.OreType;

public class OreSimulateUtil {
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

        /**
         * 計算指定高度在本特徵下生成礦石的機率，考量該高度是否於有效範圍、
         * 分佈權重、礦脈大小與水平擴散等因素。
         */
        private double pBlockLayer(Location loc, Player actor) {
            if (loc == null || loc.getWorld() == null) return 0.0;
            int y = loc.getBlockY();
            if (y < yMin || y > yMax) return 0.0;
            if (y < dYMin || y > dYMax) return 0.0;

            double chance = OreChanceResolver.resolveChanceMultiplier(loc, ore, actor);
            if (chance <= 0) return 0.0;

            double py = weights[y - dYMin] / activeWeightSum;
            double baseProbability = count * multiplier * py * (k / hSpread) / 256.0;
            return baseProbability * chance;
        }
    }

    /**
     * 快取所有預設礦石生成特徵，避免每次查詢時重新建構。
     */
    private static final List<OreFeature> BASE_FEATURE_SET = createBaseFeatureSet();

    /**
     * 建立對應原版設定的礦石特徵列表，包含生成範圍、分佈類型、礦脈大小等參數。
     */
    private static List<OreFeature> createBaseFeatureSet() {
        List<OreFeature> features = new ArrayList<>();

        // 煤礦: 主分佈(三角) + 高海拔(平均)
        features.add(new OreFeature("coal_main", OreType.COAL_ORE, 17, 20, 0, 192, 0, 192, "tri", 0.9, 3, "generic", 1.0));
        features.add(new OreFeature("coal_alt", OreType.COAL_ORE, 17, 30, 136, 256, 136, 256, "uniform", 0.9, 3, "generic", 1.0));

        // 鐵礦: 主分佈(三角) + 高海拔(三角) + 平均分佈(平均)
        features.add(new OreFeature("iron_main", OreType.IRON_ORE, 9, 10, -24, 56, -24, 56, "tri", 0.9, 3, "generic", 1.0));
        features.add(new OreFeature("iron_high", OreType.IRON_ORE, 9, 90, 80, 256, 80, 384, "tri", 0.9, 3, "generic", 1.0));
        features.add(new OreFeature("iron_alt", OreType.IRON_ORE, 4, 10, -64, 72, -64, 72, "uniform", 0.9, 3, "generic", 1.0));

        // 銅礦: 主分佈(三角)
        features.add(new OreFeature("copper_main", OreType.COPPER_ORE, 10, 16, -16, 112, -16, 112, "tri", 0.9, 3, "generic", 1.0));

        // 金礦: 主分佈(三角) + 惡地附加(平均)
        features.add(new OreFeature("gold_main", OreType.GOLD_ORE, 9, 4, -64, 32, -64, 32, "tri", 0.9, 3, "generic", 1.0));
        features.add(new OreFeature("gold_alt", OreType.GOLD_ORE, 9, 0.5, -64, -48, -64, -48, "uniform", 0.9, 3, "generic", 1.0));
        features.add(new OreFeature("gold_badlands_extra", OreType.GOLD_ORE, 9, 50, 32, 256, 32, 256, "uniform", 0.9, 3, "badlands", 1.0));

        // 青金石: 主分佈(三角) + 平均分佈(平均)
        features.add(new OreFeature("lapis_main", OreType.LAPIS_ORE, 7, 2, -32, 32, -32, 32, "tri", 0.9, 3, "generic", 1.0));
        features.add(new OreFeature("lapis_alt", OreType.LAPIS_ORE, 7, 4, -64, 64, -64, 64, "uniform", 0.9, 3, "generic", 1.0));

        // 紅石: 主分佈(三角) + 平均分佈(平均)
        features.add(new OreFeature("redstone_main", OreType.REDSTONE_ORE, 8, 8, -64, -32, -96, -32, "tri", 0.9, 3, "generic", 1.0));
        features.add(new OreFeature("redstone_alt", OreType.REDSTONE_ORE, 8, 4, -64, 16, -64, 16, "uniform", 0.9, 3, "generic", 1.0));

        // 綠寶石: 主分佈(三角)
        features.add(new OreFeature("emerald_mountain", OreType.EMERALD_ORE, 3, 100, -16, 256, -16, 480, "tri", 0.9, 3, "mountain", 1.0));

        // 鑽石: 主分佈(三角) + 掩埋分佈(三角) + 大礦分佈(三角) + 中礦分佈(均勻)
        features.add(new OreFeature("diamond_main", OreType.DIAMOND_ORE, 4, 7, -64, 16, -144, 16, "tri", 0.9, 3, "generic", 0.5));
        features.add(new OreFeature("diamond_buried", OreType.DIAMOND_ORE, 8, 4, -64, 16, -144, 16, "tri", 0.9, 3, "generic", 1.0));
        features.add(new OreFeature("diamond_large", OreType.DIAMOND_ORE, 12, 0.11, -64, 16, -144, 16, "tri", 0.9, 3, "generic", 0.3));
        features.add(new OreFeature("diamond_medium", OreType.DIAMOND_ORE, 8, 2, -64, -4, -64, -4, "uniform", 0.9, 3, "generic", 0.5));

        // 石英礦: 主分佈(平均)
        features.add(new OreFeature("nether_quartz_main", OreType.NETHER_QUARTZ_ORE, 14, 16, 10, 117, 10, 117, "uniform", 0.9, 3, "nether", 1.0));
        // 地獄金礦: 主分佈(平均)
        features.add(new OreFeature("nether_gold_main", OreType.NETHER_GOLD_ORE, 10, 10, 10, 117, 10, 117, "uniform", 0.9, 3, "nether", 1.0));
        // 遠古遺骸: 主分佈(三角) + 平均分佈(平均)
        features.add(new OreFeature("debris_main", OreType.ANCIENT_DEBRIS, 3, 1, 8, 24, 8, 24, "tri", 0.9, 3, "nether", 1.0));
        features.add(new OreFeature("debris_alt", OreType.ANCIENT_DEBRIS, 2, 1, 8, 119, 8, 119, "uniform", 0.9, 3, "nether", 1.0));

        return features;
    }

    /**
     * 依世界名稱與高度從設定檔覆寫礦脈大小，若無設定則回傳預設值。
     */
    private static final class FeatureCandidate {
        private final OreFeature feature;
        private final int veinSize;
        private double weight;

        private FeatureCandidate(OreFeature feature, int veinSize, double weight) {
            this.feature = feature;
            this.veinSize = veinSize;
            this.weight = weight;
        }
    }

    /**
     * 產生三角分佈權重陣列，模擬原版尋找高度中間偏高的產量分布。
     */
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

    /**
     * 產生均勻分佈權重陣列，代表每個高度機率相同的礦石生成。
     */
    private static double[] uniformDistribution(int a, int b) {
        int span = b - a + 1;
        double[] weights = new double[span];
        double value = 1.0 / span;
        Arrays.fill(weights, value);
        return weights;
    }

    /**
     * 根據當前生物群系模式過濾可用的礦石特徵，例如僅保留地獄或惡地專屬設定。
     */
    private static List<OreFeature> BiomeModifiers(List<OreFeature> features, BiomeMode biomeMode) {
        List<OreFeature> result = new ArrayList<>();
        for (OreFeature feature : features) {
            switch (feature.biomeTag) {
                case "generic":
                    if (biomeMode != BiomeMode.NETHER) result.add(feature);
                    break;
                case "nether":
                    if (biomeMode == BiomeMode.NETHER) result.add(feature);
                    break;
                case "badlands":
                    if (biomeMode == BiomeMode.BADLANDS) result.add(feature);
                    break;
                case "mountain":
                    if (biomeMode == BiomeMode.MOUNTAIN || biomeMode == BiomeMode.MOUNTAIN_DRIPSTONE) result.add(feature);
                    break;
                default:
                    break;
            }
        }
        return result;
    }

    public static List<String> getFeaturesName() {
        List<String> names = new ArrayList<>();
        for (OreFeature feature : BASE_FEATURE_SET) names.add(feature.name);
        return Collections.unmodifiableList(names);
    }

    public static String resolveFeatureName(String token) {
        if (token == null || token.isEmpty()) return null;
        for (OreFeature feature : BASE_FEATURE_SET) {
            if (feature.name.equalsIgnoreCase(token)) return feature.name;
        }
        return null;
    }

    public static Integer getFeatureVeinSize(String featureName) {
        return getFeatureVeinSize(featureName, null, null);
    }

    public static Integer getFeatureVeinSize(String featureName, Location loc, BiomeMode overrideBiomeMode) {
        if (featureName == null || featureName.isEmpty()) return null;
        for (OreFeature feature : BASE_FEATURE_SET) {
            if (!feature.name.equalsIgnoreCase(featureName)) continue;
            int size = feature.veinSize;
            BiomeMode biomeMode = overrideBiomeMode;
            if (biomeMode == null && loc != null) {
                biomeMode = GenericUtil.getBiomeMode(loc);
            }
            if (feature.ore == OreType.COPPER_ORE && biomeMode != null) {
                if (biomeMode == BiomeMode.DRIPSTONE || biomeMode == BiomeMode.MOUNTAIN_DRIPSTONE) {
                    size *= 2;
                }
            }
            return size;
        }
        return null;
    }

    public static Double calculateFeatureProbability(Location loc, String featureName, BiomeMode overrideBiomeMode) {
        return calculateFeatureProbability(loc, featureName, overrideBiomeMode, null);
    }

    public static Double calculateFeatureProbability(Location loc, String featureName, BiomeMode overrideBiomeMode, Player actor) {
        if (loc == null || loc.getWorld() == null) return null;
        if (featureName == null || featureName.isEmpty()) return null;

        BiomeMode biomeMode = (overrideBiomeMode != null) ? overrideBiomeMode : GenericUtil.getBiomeMode(loc);
        List<OreFeature> features = BiomeModifiers(BASE_FEATURE_SET, biomeMode);
        for (OreFeature feature : features) {
            if (feature.name.equalsIgnoreCase(featureName)) {
                double probability = feature.pBlockLayer(loc, actor);
                return (probability >= 0.0) ? probability : 0.0;
            }
        }
        return null;
    }

    /**
     * 根據方塊位置挑選最可能生成的礦種與對應礦脈資訊，供實際替換流程使用。
     */
    public static OreSelection getMostLikelyOre(Block block) {
        return getMostLikelyOre(block, null);
    }

    public static OreSelection getMostLikelyOre(Block block, Player actor) {
        if (block == null) return null;

        Location loc = block.getLocation();
        BiomeMode biomeMode = GenericUtil.getBiomeMode(loc);

        List<OreFeature> features = BiomeModifiers(BASE_FEATURE_SET, biomeMode);
        if (features.isEmpty()) return null;

        Map<OreType, Double> remainingPerOre = new EnumMap<>(OreType.class);
        List<FeatureCandidate> candidates = new ArrayList<>();

        for (OreFeature feature : features) {
            double probability = feature.pBlockLayer(loc, actor);
            if (probability <= 0) continue;

            double remaining = remainingPerOre.getOrDefault(feature.ore, 1.0);
            if (remaining <= 0) continue;

            double weight = remaining * probability;
            if (weight > 0) {
                candidates.add(new FeatureCandidate(feature, feature.veinSize, weight));
            }

            remainingPerOre.put(feature.ore, remaining * (1.0 - probability));
        }

        double totalWeight = 0.0;
        for (FeatureCandidate candidate : candidates) {
            totalWeight += candidate.weight;
        }

        if (totalWeight <= 0) return null;

        double normalizer = Math.max(totalWeight, 1.0);
        double r = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0.0;
        for (FeatureCandidate candidate : candidates) {
            cumulative += candidate.weight / normalizer;
            if (r < cumulative) {
                OreType oreType = candidate.feature.ore;
                if (oreType.name().startsWith("NETHER_") && !block.getType().equals(Material.NETHERRACK)) return null;
                return new OreSelection(oreType, candidate.veinSize, candidate.feature.name);
            }
        }

        return null;
    }
}
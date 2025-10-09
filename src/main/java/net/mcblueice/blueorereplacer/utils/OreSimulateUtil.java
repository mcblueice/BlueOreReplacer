package net.mcblueice.blueorereplacer.utils;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import net.mcblueice.blueorereplacer.BlueOreReplacer;
import net.mcblueice.blueorereplacer.utils.GenericUtil.BiomeMode;
import net.mcblueice.blueorereplacer.utils.GenericUtil.OreSelection;
import net.mcblueice.blueorereplacer.utils.GenericUtil.OreType;

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

        /**
         * 計算指定高度在本特徵下生成礦石的機率，考量該高度是否於有效範圍、
         * 分佈權重、礦脈大小與水平擴散等因素。
         */
        private double pBlockLayer(Location loc) {
            if (loc == null || loc.getWorld() == null) return 0.0;
            int y = loc.getBlockY();
            if (y < yMin || y > yMax) return 0.0;
            if (y < dYMin || y > dYMax) return 0.0;

            String worldName = loc.getWorld().getName();
            String basePath = "OresGeneration." + worldName + "." + GenericUtil.getOreName(ore, y);
            if (!plugin().getConfig().getBoolean(basePath + ".enabled", true)) return 0.0;

            double chance = 1.0;
            Object rawChance = plugin().getConfig().get(basePath + ".chance");
            if (rawChance instanceof Number num) {
                chance = num.doubleValue();
            }
            if (chance <= 0) return 0.0;

            double py = weights[y - dYMin] / activeWeightSum;
            double baseProbability = count * multiplier * py * (k * veinSize / hSpread) / 256.0;
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

        // 鑽石: 主分佈(三角) + 掩埋分佈(三角) + 大礦分佈(三角) + 中礦分佈(均勻)
        features.add(new OreFeature("diamond_main", OreType.DIAMOND_ORE, 4, 7, -64, 16, -64, 16, "tri", 0.9, 3, "generic", 0.5));
        features.add(new OreFeature("diamond_buried", OreType.DIAMOND_ORE, 8, 4, -64, 16, -64, 16, "tri", 0.9, 3, "generic", 1.0));
        features.add(new OreFeature("diamond_large", OreType.DIAMOND_ORE, 12, 9, -64, 16, -64, 16, "tri", 0.9, 3, "generic", 1.0 / 9.0 * 0.3));
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
        if (featureName == null || featureName.isEmpty()) return null;
        for (OreFeature feature : BASE_FEATURE_SET) {
            if (feature.name.equalsIgnoreCase(featureName)) return feature.veinSize;
        }
        return null;
    }

    public static Double calculateFeatureProbability(Location loc, String featureName, BiomeMode overrideBiomeMode) {
        if (loc == null || loc.getWorld() == null) return null;
        if (featureName == null || featureName.isEmpty()) return null;

        BiomeMode biomeMode = (overrideBiomeMode != null) ? overrideBiomeMode : GenericUtil.getBiomeMode(loc);
        List<OreFeature> features = BiomeModifiers(BASE_FEATURE_SET, biomeMode);
        for (OreFeature feature : features) {
            if (feature.name.equalsIgnoreCase(featureName)) {
                double probability = feature.pBlockLayer(loc);
                return (probability >= 0.0) ? probability : 0.0;
            }
        }
        return null;
    }

    /**
     * 根據方塊位置挑選最可能生成的礦種與對應礦脈資訊，供實際替換流程使用。
     */
    public static OreSelection getMostLikelyOre(Block block) {
        if (block == null) return null; // 只有實際方塊才有機會生成礦物

        Location loc = block.getLocation(); // 取得方塊座標資料，後續需要世界、高度與生物群系資訊
        BiomeMode biomeMode = GenericUtil.getBiomeMode(loc); // 根據座標判斷當前生物群系模式（地表、地獄、惡地等）

        List<OreFeature> features = BiomeModifiers(BASE_FEATURE_SET, biomeMode); // 過濾出對應生物群系允許的礦石特徵組合
        if (features.isEmpty()) return null; // 如果當前生物群系沒有任何礦種可用，直接回傳 null

        Map<OreType, Double> remainingPerOre = new EnumMap<>(OreType.class); // 紀錄每種礦的剩餘可用機率，模擬原版逐步消耗行為
        List<FeatureCandidate> candidates = new ArrayList<>(); // 收集符合條件的候選特徵，稍後做加權抽選

        for (OreFeature feature : features) { // 遍歷所有符合生物群系的特徵
            double probability = feature.pBlockLayer(loc); // 計算此特徵在當前高度的生成機率（已套用世界設定）
            if (probability <= 0) continue; // 若在這高度該特徵沒有產量，跳過

            double remaining = remainingPerOre.getOrDefault(feature.ore, 1.0); // 取得該礦種目前還剩多少未被消耗的機率
            if (remaining <= 0) continue; // 若剩餘量耗盡，代表早一步的特徵已經用完機率了，跳過

            double weight = remaining * probability; // 將剩餘量與此特徵機率相乘得到此次抽選權重
            if (weight > 0) {
                candidates.add(new FeatureCandidate(feature, feature.veinSize, weight)); // 權重大於 0 才有意義，加入候選清單
            }

            remainingPerOre.put(feature.ore, remaining * (1.0 - probability)); // 更新該礦種剩餘量，模擬原版多特徵相減的行為
        }

        double totalWeight = 0.0; // 加總所有候選權重，後面用來歸一化
        for (FeatureCandidate candidate : candidates) {
            totalWeight += candidate.weight; // 對候選列表逐一相加
        }

        if (totalWeight <= 0) return null; // 沒有可用權重代表沒有任何礦種能在此高度生成

        double normalizer = Math.max(totalWeight, 1.0); // 避免 totalWeight 小於 1 時造成抽選過度偏差，至少取 1
        double r = ThreadLocalRandom.current().nextDouble(); // 產生 [0,1) 的隨機值，用於加權抽籤
        double cumulative = 0.0; // 累計權重比例
        for (FeatureCandidate candidate : candidates) {
            cumulative += candidate.weight / normalizer; // 將個別候選的權重轉換成累積機率
            if (r < cumulative) { // 找出第一個累積值超過隨機數的候選，即為抽選結果
                OreType oreType = candidate.feature.ore; // 取得當選的礦種
                if (oreType.name().startsWith("NETHER_") && !block.getType().equals(Material.NETHERRACK)) return null; // 檢查地獄礦物是否對應正確的方塊材質
                return new OreSelection(oreType, candidate.veinSize, candidate.feature.name); // 回傳包含礦種、礦脈大小與特徵名稱的結果
            }
        }

        return null; // 正常狀況應已在迴圈內回傳，這裡純粹保險
    }
}
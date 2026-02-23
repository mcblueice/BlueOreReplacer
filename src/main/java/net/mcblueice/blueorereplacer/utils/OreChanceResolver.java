package net.mcblueice.blueorereplacer.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import net.mcblueice.blueorereplacer.BlueOreReplacer;
import net.mcblueice.blueorereplacer.utils.GenericUtil.OreType;

public final class OreChanceResolver {

    private static final String ROOT_PATH = "OresGeneration";
    private static final String PERM_PREFIX = "blueoreplacer.chance.";

    private static final OreConfigEntry DEFAULT_ENTRY = new OreConfigEntry(true, 1.0D);
    private static final OreConfigEntry DISABLED_ENTRY = new OreConfigEntry(false, 0.0D);
    private static volatile Map<String, Map<String, OreConfigEntry>> configCache = Collections.emptyMap();
    private static volatile boolean playerChanceEnabled = true;

    private static final ConcurrentHashMap<UUID, PlayerChanceCache> playerChanceCache = new ConcurrentHashMap<>();

    private OreChanceResolver() {
    }

    public static void reload() {
        rebuildConfigCache();
        playerChanceCache.clear();
    }

    public static void setPlayerChanceEnabled(boolean enabled) {
        if (playerChanceEnabled == enabled) return;
        playerChanceEnabled = enabled;
        playerChanceCache.clear();
    }

    public static void warmupPlayer(Player player) {
        if (!playerChanceEnabled) return;
        if (player == null) return;
        Map<OreType, Double> perOre = new EnumMap<>(OreType.class);
        double wildcard = 0.0D;

        Set<PermissionAttachmentInfo> effectivePermissions = player.getEffectivePermissions();
        for (PermissionAttachmentInfo info : effectivePermissions) {
            if (!info.getValue()) continue;

            String permission = info.getPermission();
            if (permission == null || permission.isEmpty()) continue;

            String lower = permission.toLowerCase(Locale.ROOT);
            if (!lower.startsWith(PERM_PREFIX)) continue;

            String suffix = permission.substring(PERM_PREFIX.length());
            int dotPos = suffix.lastIndexOf('.');
            if (dotPos <= 0 || dotPos >= suffix.length() - 1) continue;

            String oreToken = suffix.substring(0, dotPos).trim();
            String pctToken = suffix.substring(dotPos + 1).trim();
            if (oreToken.isEmpty() || pctToken.isEmpty()) continue;

            int percent;
            try {
                percent = Integer.parseInt(pctToken);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (percent < 0) continue;

            double bonus = percent / 100.0D;
            if ("*".equals(oreToken)) {
                wildcard = Math.max(wildcard, bonus);
                continue;
            }

            OreType oreType = GenericUtil.stringToOreType(oreToken);
            if (oreType == null) continue;

            perOre.merge(oreType, bonus, Math::max);
        }

        playerChanceCache.put(player.getUniqueId(), new PlayerChanceCache(perOre, wildcard));
    }

    public static void invalidatePlayer(UUID uuid) {
        if (uuid == null) return;
        playerChanceCache.remove(uuid);
    }

    public static double resolveChanceMultiplier(Location loc, OreType oreType, Player player) {
        if (loc == null || loc.getWorld() == null || oreType == null) return 0.0D;

        OreConfigEntry configEntry = resolveConfigEntry(loc, oreType);
        if (!configEntry.enabled()) return 0.0D;

        double permChance = resolvePermissionChance(player, oreType);
        double combined = configEntry.chance() + permChance;
        if (Double.isNaN(combined) || Double.isInfinite(combined)) return 0.0D;
        return Math.max(0.0D, combined);
    }

    private static OreConfigEntry resolveConfigEntry(Location loc, OreType oreType) {
        World world = loc.getWorld();
        List<String> worldCandidates = getWorldConfigCandidates(world);
        if (worldCandidates.isEmpty()) return DISABLED_ENTRY;

        List<String> oreCandidates = new ArrayList<>(2);
        oreCandidates.add(oreType.name());
        oreCandidates.add(GenericUtil.getOreName(oreType, loc.getBlockY()));

        for (String worldKey : worldCandidates) {
            Map<String, OreConfigEntry> oreMap = configCache.get(worldKey.toLowerCase(Locale.ROOT));
            if (oreMap == null || oreMap.isEmpty()) continue;
            for (String oreKey : oreCandidates) {
                OreConfigEntry entry = oreMap.get(oreKey.toUpperCase(Locale.ROOT));
                if (entry != null) return entry;
            }
        }

        return DEFAULT_ENTRY;
    }

    private static double resolvePermissionChance(Player player, OreType oreType) {
        if (!playerChanceEnabled) return 0.0D;
        if (player == null || oreType == null) return 0.0D;

        PlayerChanceCache cache = playerChanceCache.get(player.getUniqueId());
        if (cache == null) return 0.0D;
        return cache.bonusFor(oreType);
    }

    private static void rebuildConfigCache() {
        BlueOreReplacer plugin = BlueOreReplacer.getInstance();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection(ROOT_PATH);
        if (root == null) {
            configCache = Collections.emptyMap();
            return;
        }

        Map<String, Map<String, OreConfigEntry>> next = new ConcurrentHashMap<>();

        for (String worldKey : root.getKeys(false)) {
            ConfigurationSection worldSection = root.getConfigurationSection(worldKey);
            if (worldSection == null) continue;

            Map<String, OreConfigEntry> oreMap = new ConcurrentHashMap<>();
            for (String oreKey : worldSection.getKeys(false)) {
                String basePath = ROOT_PATH + "." + worldKey + "." + oreKey;
                if (!plugin.getConfig().isConfigurationSection(basePath)) continue;

                boolean enabled = true;
                if (plugin.getConfig().contains(basePath + ".enabled")) {
                    enabled = plugin.getConfig().getBoolean(basePath + ".enabled", true);
                } else if (plugin.getConfig().contains(basePath + ".enable")) {
                    enabled = plugin.getConfig().getBoolean(basePath + ".enable", true);
                }

                double chance = 1.0D;
                Object chanceRaw = plugin.getConfig().get(basePath + ".chance");
                if (chanceRaw instanceof Number number) {
                    chance = number.doubleValue();
                }

                oreMap.put(oreKey.toUpperCase(Locale.ROOT), new OreConfigEntry(enabled, chance));
            }

            next.put(worldKey.toLowerCase(Locale.ROOT), Collections.unmodifiableMap(oreMap));
        }

        configCache = Collections.unmodifiableMap(next);
    }

    private static List<String> getWorldConfigCandidates(World world) {
        Environment environment = world.getEnvironment();
        switch (environment) {
            case NORMAL: return List.of("world");
            case NETHER: return List.of("world_nether");
            case THE_END: return List.of("world_the_end");
            default: return List.of();
        }
    }

    private record OreConfigEntry(boolean enabled, double chance) {
    }

    private record PlayerChanceCache(Map<OreType, Double> perOreBonus, double wildcardBonus) {
        double bonusFor(OreType oreType) {
            double specific = perOreBonus.getOrDefault(oreType, 0.0D);
            return Math.max(specific, wildcardBonus);
        }
    }
}

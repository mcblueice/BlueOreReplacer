package net.mcblueice.blueorereplacer;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import net.mcblueice.blueorereplacer.utils.GenericUtil;
import net.mcblueice.blueorereplacer.utils.GenericUtil.OreType;
import net.mcblueice.blueorereplacer.utils.OreSimulateUtil;

public class BlueOreReplacerExpansion extends PlaceholderExpansion {

    private final BlueOreReplacer plugin;

    public BlueOreReplacerExpansion(BlueOreReplacer plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "blueorereplacer";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() { return true; }

    @Override
    public boolean canRegister() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) return null;
        if (identifier == null || identifier.isEmpty()) return "";

        String lower = identifier.toLowerCase(Locale.ROOT);
        if (lower.startsWith("simulate_chance_")) {
            String typeToken = identifier.substring("simulate_chance_".length());
            return formatChancePlaceholder(player, typeToken);
        }
        if (lower.startsWith("simulate_maxvein_")) {
            String typeToken = identifier.substring("simulate_maxvein_".length());
            return formatMaxVeinPlaceholder(player, typeToken);
        }
        return "";
    }

    private String formatChancePlaceholder(Player player, String rawType) {
        OreType oreType = parseOreType(rawType);
        if (oreType == null) {
            return "invalid";
        }
        Location loc = player.getLocation();
        Double probability = OreSimulateUtil.calculateOreProbability(loc, oreType);
        if (probability == null) {
            return "N/A";
        }
        Map<OreType, Double> weights = new EnumMap<>(OreType.class);
        double sum = 0.0D;
        for (OreType type : OreType.values()) {
            Double value = OreSimulateUtil.calculateOreProbability(loc, type);
            if (value != null && value > 0) {
                weights.put(type, value);
                sum += value;
            }
        }
        double effective = 0.0D;
        if (probability > 0) {
            effective = sum <= 1.0D ? probability : probability / sum;
        }
        double pct = Math.max(0.0D, Math.min(1.0D, effective)) * 100.0D;
        return String.format(Locale.US, "%.3f", pct);
    }

    private String formatMaxVeinPlaceholder(Player player, String rawType) {
        OreType oreType = parseOreType(rawType);
        if (oreType == null) {
            return "invalid";
        }
        int maxSize = OreSimulateUtil.getVeinSize(player.getLocation(), oreType);
        return Integer.toString(maxSize);
    }

    private OreType parseOreType(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        return GenericUtil.stringToOreType(token.replace('-', '_'));
    }
}

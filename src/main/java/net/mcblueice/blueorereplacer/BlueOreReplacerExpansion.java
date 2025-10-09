package net.mcblueice.blueorereplacer;

import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

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
        return "";
    }

    private String formatChancePlaceholder(Player player, String rawType) {
        String featureName = OreSimulateUtil.resolveFeatureName(rawType);
        if (featureName == null) {
            return "invalid";
        }
        Location loc = player.getLocation();
        Double probability = OreSimulateUtil.calculateFeatureProbability(loc, featureName, null);
        if (probability == null) {
            return "N/A";
        }
        double pct = Math.max(0.0D, probability) * 100.0D;
        return String.format(Locale.US, "%.3f", pct);
    }
}

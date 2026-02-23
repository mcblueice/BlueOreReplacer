package net.mcblueice.blueorereplacer;

import java.util.logging.Logger;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.mcblueice.blueorereplacer.utils.ConfigManager;
import net.mcblueice.blueorereplacer.utils.OreReplaceUtil;
import net.mcblueice.blueorereplacer.tracker.BlockStateTracker;
import net.mcblueice.blueorereplacer.listener.BlockChangeListener;
import net.mcblueice.blueorereplacer.listener.CheckModeListener;
import net.mcblueice.blueorereplacer.listener.ChunkListener;
import net.mcblueice.blueorereplacer.listener.PlayerChanceCacheListener;

public final class BlueOreReplacer extends JavaPlugin {
    private static BlueOreReplacer instance;
    private ConfigManager lang;
    private Logger logger;
    private BlockStateTracker blockTracker;
    private BlockChangeListener blockChangeListener;
    private PlayerChanceCacheListener playerChanceCacheListener;
    public static final Set<UUID> checkModePlayers = ConcurrentHashMap.newKeySet();
    public static final Set<UUID> debugModePlayers = ConcurrentHashMap.newKeySet();
    private static final UUID CONSOLE_UUID = new UUID(0L, 0L);
    public static String prefix = "§7§l[§b§l礦物§7§l]";
    public static boolean debug = false;

	@Override
	public void onEnable() {
        instance = this;
        logger = getLogger();

        if (!isPaperServer()) {
            logger.severe("此插件僅支持在 Paper 以及其分支上運行");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        lang = new ConfigManager(this);
        blockTracker = new BlockStateTracker(this);

		Commands commands = new Commands(this);
        getCommand("blueoreplacer").setExecutor(commands);
        getCommand("blueoreplacer").setTabCompleter(commands);

        OreReplaceUtil.reload();
        debug = getConfig().getBoolean("Debug", false);
        prefix = lang.get("Prefix");

        blockChangeListener = new BlockChangeListener(this);
        getServer().getPluginManager().registerEvents(blockChangeListener, this);
        getServer().getPluginManager().registerEvents(new CheckModeListener(this), this);
        getServer().getPluginManager().registerEvents(new ChunkListener(this), this);
        playerChanceCacheListener = new PlayerChanceCacheListener(this);
        getServer().getPluginManager().registerEvents(playerChanceCacheListener, this);
        playerChanceCacheListener.bootstrap();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BlueOreReplacerExpansion(this).register();
            logger.info("已註冊 PlaceholderAPI 擴展");
        }

        logger.info("BlueOreReplacer 已啟動");
	}

    @Override
    public void onDisable() {
        if (playerChanceCacheListener != null) playerChanceCacheListener.shutdown();
        if (blockTracker != null) blockTracker.shutdown();
		logger.info("BlueOreReplacer 已卸載");
	}

    public static BlueOreReplacer getInstance() { return instance; }
    public ConfigManager getLanguageManager() { return lang; }
    public BlockStateTracker getBlockTracker() { return blockTracker; }
    public BlockChangeListener getBlockChangeListener() { return blockChangeListener; }
    public PlayerChanceCacheListener getPlayerChanceCacheListener() { return playerChanceCacheListener; }

    public boolean toggleCheckMode(UUID uuid) {
        if (uuid == null) return false;
        if (checkModePlayers.contains(uuid)) {
            checkModePlayers.remove(uuid);
            return false;
        } else {
            checkModePlayers.add(uuid);
            return true;
        }
    }

    public void setDebugMode(boolean enabled) {
        debug = enabled;
    }
    public boolean getDebugMode() {
        return debug;
    }

    public void setPrefix(String newPrefix) {
        prefix = newPrefix;
    }
    public String getPrefix() {
        return prefix;
    }

    public boolean isInCheckMode(UUID uuid) {
        if (uuid == null) return false;
        return checkModePlayers.contains(uuid);
    }

    public boolean toggleDebugMode(UUID uuid) {
        if (uuid == null) return false;
        if (debugModePlayers.contains(uuid)) {
            debugModePlayers.remove(uuid);
            return false;
        } else {
            debugModePlayers.add(uuid);
            return true;
        }
    }

    public static void sendDebug(String message) {
        if (message == null) return;
        if (debugModePlayers.isEmpty()) return;

        // console
        if (debugModePlayers.contains(CONSOLE_UUID)) Bukkit.getConsoleSender().sendMessage(prefix + "§eDEBUG: §7" + message);
        // player
        for (UUID uuid : debugModePlayers) {
            if (uuid.equals(CONSOLE_UUID)) continue;
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) player.sendMessage(prefix + "§eDEBUG: §7" + message);
        }
    }

    public static void sendMessage(String message) {
        sendMessage(prefix, message);
    }
    public static void sendMessage(String prefix, String message) {
        if (message == null) return;
        Bukkit.getConsoleSender().sendMessage(prefix + message);
    }

    public static void sendMessage(Player player, String message) {
        sendMessage(player, prefix, message);
    }
    public static void sendMessage(Player player, String prefix, String message) {
        if (player == null || message == null) return;
        player.sendMessage(prefix + message);
    }

    // Paper
    private boolean isPaperServer() {
        try {
            Class.forName("io.papermc.paper.configuration.Configuration");
            return true;
        } catch (Throwable ignored) {}
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            return true;
        } catch (Throwable ignored) {}
        String ver = getServer().getVersion().toLowerCase(java.util.Locale.ROOT);
        return ver.contains("paper") || ver.contains("purpur") || ver.contains("pufferfish")
                || ver.contains("tuinity") || ver.contains("folia") || ver.contains("leaf")
                || ver.contains("airplane");
    }
	
}

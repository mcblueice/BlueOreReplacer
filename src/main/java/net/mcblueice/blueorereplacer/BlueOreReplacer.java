package net.mcblueice.blueorereplacer;

import java.util.logging.Logger;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.mcblueice.blueorereplacer.utils.TaskScheduler;

import net.mcblueice.blueorereplacer.utils.ConfigManager;
import net.mcblueice.blueorereplacer.utils.ChunkModificationTracker;
import net.mcblueice.blueorereplacer.listener.BlockChangeListener;
import net.mcblueice.blueorereplacer.listener.CheckModeListener;
import net.mcblueice.blueorereplacer.listener.ChunkListener;

public final class BlueOreReplacer extends JavaPlugin {
    private static BlueOreReplacer instance;
    private ConfigManager languageManager;
    private Logger logger;
    private ChunkModificationTracker chunkTracker;
    public final Set<UUID> checkModePlayers = ConcurrentHashMap.newKeySet();
    public final Set<UUID> debugModePlayers = ConcurrentHashMap.newKeySet();

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
        languageManager = new ConfigManager(this);
        chunkTracker = new ChunkModificationTracker(this);

		Commands commands = new Commands(this);
        getCommand("blueoreplacer").setExecutor(commands);
        getCommand("blueoreplacer").setTabCompleter(commands);

        getServer().getPluginManager().registerEvents(new BlockChangeListener(this), this);
        getServer().getPluginManager().registerEvents(new CheckModeListener(this), this);
        getServer().getPluginManager().registerEvents(new ChunkListener(this), this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BlueOreReplacerExpansion(this).register();
            logger.info("已註冊 PlaceholderAPI 擴展");
        }

        logger.info("BlueOreReplacer 已啟動");
	}

	@Override
	public void onDisable() {
        if (chunkTracker != null) chunkTracker.shutdown();
		logger.info("BlueOreReplacer 已卸載");
	}

    public static BlueOreReplacer getInstance() { return instance; }
    public ConfigManager getLanguageManager() { return languageManager; }
    public ChunkModificationTracker getChunkTracker() { return chunkTracker; }

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

    public void sendDebug(String message) {
        if (message == null) return;
        if (debugModePlayers.isEmpty()) return;

        // console
        if (debugModePlayers.contains(new UUID(0L, 0L))) {
            TaskScheduler.runTask(this, () -> Bukkit.getConsoleSender().sendMessage("§7§l[§b§l礦物§7§l]§eDEBUG: §7" + message));
        }
        // player
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!debugModePlayers.contains(player.getUniqueId())) continue;
            TaskScheduler.runTask(player, this, () -> player.sendMessage("§7§l[§b§l礦物§7§l]§eDEBUG: §7" + message));
        }
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

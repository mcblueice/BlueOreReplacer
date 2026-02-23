package net.mcblueice.blueorereplacer.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.node.NodeAddEvent;
import net.luckperms.api.event.node.NodeClearEvent;
import net.luckperms.api.event.node.NodeRemoveEvent;
import net.luckperms.api.model.user.User;
import net.mcblueice.blueorereplacer.BlueOreReplacer;

import net.mcblueice.blueorereplacer.utils.OreChanceResolver;
import net.mcblueice.blueorereplacer.utils.TaskScheduler;

public class PlayerChanceCacheListener implements Listener {

    private final BlueOreReplacer plugin;
    private final List<EventSubscription<?>> luckPermsSubs = new ArrayList<>();

    public PlayerChanceCacheListener(BlueOreReplacer plugin) {
        this.plugin = plugin;
    }

    public void bootstrap() {
        boolean LuckPermsEnabled = plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms");
        OreChanceResolver.setPlayerChanceEnabled(LuckPermsEnabled);
        OreChanceResolver.reload();
        if (!LuckPermsEnabled) {
            if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("未檢測到 LuckPerms 玩家機率功能已關閉");
            return;
        }
        hookLuckPermsEvents();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            TaskScheduler.runTask(player, plugin, () -> OreChanceResolver.warmupPlayer(player));
        }
    }

    public void reload() {
        boolean LuckPermsEnabled = plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms");
        OreChanceResolver.setPlayerChanceEnabled(LuckPermsEnabled);
        OreChanceResolver.reload();
        if (!LuckPermsEnabled) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            TaskScheduler.runTask(player, plugin, () -> OreChanceResolver.warmupPlayer(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        OreChanceResolver.warmupPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        OreChanceResolver.invalidatePlayer(event.getPlayer().getUniqueId());
    }

    private synchronized void hookLuckPermsEvents() {
        if (!luckPermsSubs.isEmpty()) shutdown();

        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            EventBus eventBus = luckPerms.getEventBus();

            luckPermsSubs.add(eventBus.subscribe(plugin, NodeAddEvent.class, event -> {
                if (!(event.getTarget() instanceof User user)) return;
                refreshUser(user.getUniqueId());
            }));
            luckPermsSubs.add(eventBus.subscribe(plugin, NodeRemoveEvent.class, event -> {
                if (!(event.getTarget() instanceof User user)) return;
                refreshUser(user.getUniqueId());
            }));
            luckPermsSubs.add(eventBus.subscribe(plugin, NodeClearEvent.class, event -> {
                if (!(event.getTarget() instanceof User user)) return;
                refreshUser(user.getUniqueId());
            }));

            if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug("LuckPerms 權限事件監聽已掛載");
        } catch (IllegalStateException ex) {}
    }

    public synchronized void shutdown() {
        for (EventSubscription<?> sub : luckPermsSubs) {
            try {
                sub.close();
            } catch (Throwable ignored) {}
        }
        luckPermsSubs.clear();
    }

    private void refreshUser(UUID uuid) {
        TaskScheduler.runTask(plugin, () -> {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null) {
                OreChanceResolver.invalidatePlayer(uuid);
                return;
            }
            TaskScheduler.runTask(player, plugin, () -> OreChanceResolver.warmupPlayer(player));
        });
    }
}

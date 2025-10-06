package net.mcblueice.blueorereplacer.listener;

import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;

import net.mcblueice.blueorereplacer.BlueOreReplacer;
import net.mcblueice.blueorereplacer.utils.ChunkModificationTracker;
import net.mcblueice.blueorereplacer.utils.TaskScheduler;

public class CheckModeListener implements Listener {
    private final BlueOreReplacer plugin;
    private static final String PREFIX = "§7§l[§b§l礦物§7§l]§r";

    public CheckModeListener(BlueOreReplacer plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!plugin.checkModePlayers.contains(player.getUniqueId())) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        var tracker = plugin.getChunkTracker();
        if (player.isSneaking()) {
            Chunk chunk = block.getChunk();
            ChunkModificationTracker.ChunkStats stats = tracker.getChunkStats(chunk);
            var keys = tracker.getChunkPdcKeys(chunk);
            TaskScheduler.runTask(player, plugin, () -> {
                player.sendMessage("§8========== §b區塊資訊 §8==========");
                player.sendMessage("§7世界: §e" + chunk.getWorld().getName() + " §7區塊: §e" + chunk.getX() + ", " + chunk.getZ());
                if (stats.height() == 0) {
                    player.sendMessage("§7高度範圍: §e無");
                } else {
                    player.sendMessage("§7高度範圍: §e" + stats.baseY() + " ~ " + (stats.baseY() + stats.height() - 1) + " §8(§6" + stats.height() + "§8)");
                }
                player.sendMessage("§7容量(格): §e" + stats.capacity() + " §7已標記: §e" + stats.modified() + " §7比例: §e" + stats.ratioPercent());
                player.sendMessage("§7方塊更新序列化大小: §e" + stats.bytes() + " bytes");
                if (keys.isEmpty()) {
                    player.sendMessage("§7PDC Keys: §8無");
                } else {
                    player.sendMessage("§7PDC Keys: §f" + keys.stream().map(k -> k.getNamespace() + ":" + k.getKey()).sorted().reduce((a,b)-> a+", "+b).orElse(""));
                }
                player.sendMessage("§8===========================");
            });
        } else {
            boolean modified = tracker.isModified(block);
            TaskScheduler.runTask(player, plugin, () -> player.sendMessage(PREFIX + "§e世界: §e" + block.getWorld().getName() + " §9座標 §c" + block.getX() + " §a" + block.getY() + " §b" + block.getZ() + " §7狀態: " + (modified ? "§6人工方塊" : "§2自然方塊")));
        }
    }
}

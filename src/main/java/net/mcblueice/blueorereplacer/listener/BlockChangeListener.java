package net.mcblueice.blueorereplacer.listener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

import net.mcblueice.blueorereplacer.BlueOreReplacer;
import net.mcblueice.blueorereplacer.utils.OreReplaceUtil;


public class BlockChangeListener implements Listener {
    private final BlueOreReplacer plugin;

    public BlockChangeListener(BlueOreReplacer plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        plugin.getChunkTracker().markModified(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        plugin.sendDebug(String.format(
            "玩家破壞: §e%s §7by §6%s §7@ §9%s §c%d §a%d §b%d",
            block.getType().name(),
            event.getPlayer().getName(),
            (loc != null ? loc.getWorld().getName() : "unknown"),
            (loc != null ? loc.getBlockX() : 0),
            (loc != null ? loc.getBlockY() : 0),
            (loc != null ? loc.getBlockZ() : 0)
        ));
        OreReplaceUtil.tryReplaceNeighbors(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        Entity entity = event.getEntity();
        plugin.sendDebug(String.format(
            "實體變更: §e%s §e7-> §e%s §7by §6%s §7@ §9%s §c%d §a%d §b%d",
            block.getType().name(),
            (event.getTo() != null ? event.getTo().name() : "(no-change)"),
            (entity != null ? entity.getType().name() : "unknown"),
            (loc != null ? loc.getWorld().getName() : "unknown"),
            (loc != null ? loc.getBlockX() : 0),
            (loc != null ? loc.getBlockY() : 0),
            (loc != null ? loc.getBlockZ() : 0)
        ));
        OreReplaceUtil.tryReplaceNeighbors(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectBlocksBeforeEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            return block.getType().equals(Material.ANCIENT_DEBRIS);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        Location  loc = entity.getLocation();

        List<Block> explodedBlocks = event.blockList();
        Set<Long> explodedBlockSet = new HashSet<>(explodedBlocks.size() * 2);

        plugin.sendDebug(String.format(
            "實體爆炸: §6%s §7@ §9%s §c%d §a%d §b%d §7影響數: §e%d",
            (entity != null ? entity.getType().name() : "unknown"),
            (loc != null ? loc.getWorld().getName() : "unknown"),
            (loc != null ? loc.getBlockX() : 0),
            (loc != null ? loc.getBlockY() : 0),
            (loc != null ? loc.getBlockZ() : 0),
            explodedBlocks.size()
        ));

        for (Block block : explodedBlocks) {
            explodedBlockSet.add(encode(block.getX(), block.getY(), block.getZ()));
            OreReplaceUtil.tryReplace(block, null, false);
        }

        Set<Block> outerEdge = new HashSet<>();
        int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (Block block : explodedBlocks) {
            int x = block.getX(), y = block.getY(), z = block.getZ();
            for (int[] d : dirs) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                long key = encode(nx, ny, nz);
                if (!explodedBlockSet.contains(key)) {
                    Block neighbor = block.getWorld().getBlockAt(nx, ny, nz);
                    if (neighbor.getType() != Material.AIR) {
                        outerEdge.add(neighbor);
                    }
                }
            }
        }
        for (Block block : outerEdge) {
            OreReplaceUtil.tryReplace(block, null, true);
        }

    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectBlocksBeforeBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            return block.getType().equals(Material.ANCIENT_DEBRIS);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            Location loc = block.getLocation();
            plugin.sendDebug(String.format(
                "方塊爆炸: §e%s §7@ §9%s §c%d §a%d §b%d §7影響數: §e%d",
                block.getType().name(),
                (loc != null ? loc.getWorld().getName() : "unknown"),
                (loc != null ? loc.getBlockX() : 0),
                (loc != null ? loc.getBlockY() : 0),
                (loc != null ? loc.getBlockZ() : 0),
                event.blockList().size()
            ));
            OreReplaceUtil.tryReplaceNeighbors(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        event.getBlocks().forEach(block -> {
            Location loc = block.getLocation();
            plugin.sendDebug(String.format(
                "活塞推出: §e%s §7@ §9%s §c%d §a%d §b%d §7影響數: §e%d",
                block.getType().name(),
                (loc != null ? loc.getWorld().getName() : "unknown"),
                (loc != null ? loc.getBlockX() : 0),
                (loc != null ? loc.getBlockY() : 0),
                (loc != null ? loc.getBlockZ() : 0),
                event.getBlocks().size()
            ));
            OreReplaceUtil.tryReplaceNeighbors(block);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        event.getBlocks().forEach(block -> {
            Location loc = block.getLocation();
            plugin.sendDebug(String.format(
                "活塞拉回: §e%s §7@ §9%s §c%d §a%d §b%d §7影響數: §e%d",
                block.getType().name(),
                (loc != null ? loc.getWorld().getName() : "unknown"),
                (loc != null ? loc.getBlockX() : 0),
                (loc != null ? loc.getBlockY() : 0),
                (loc != null ? loc.getBlockZ() : 0),
                event.getBlocks().size()
            ));
            OreReplaceUtil.tryReplaceNeighbors(block);
        });
    }

    private long encode(int x, int y, int z) {
        return (((long)x & 0x3FFFFFFL) << 38) | (((long)z & 0x3FFFFFFL) << 12) | ((long)y & 0xFFFL);
    }
}

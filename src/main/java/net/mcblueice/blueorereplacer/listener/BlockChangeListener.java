package net.mcblueice.blueorereplacer.listener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.projectiles.ProjectileSource;

import net.mcblueice.blueorereplacer.BlueOreReplacer;
import net.mcblueice.blueorereplacer.utils.OreReplaceUtil;


public class BlockChangeListener implements Listener {
    private static final long INTERACT_CACHE_TTL_MILLIS = 3000L;

    private final ConcurrentHashMap<BlockCacheKey, CachedActor> blockInteractActorCache = new ConcurrentHashMap<>();
    private final BlueOreReplacer plugin;

    public BlockChangeListener(BlueOreReplacer plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        plugin.getBlockTracker().markModified(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug(String.format(
            "玩家破壞: §e%s §7by §6%s §7@ §9%s §c%d §a%d §b%d",
            block.getType().name(),
            event.getPlayer().getName(),
            (loc != null ? loc.getWorld().getName() : "unknown"),
            (loc != null ? loc.getBlockX() : 0),
            (loc != null ? loc.getBlockY() : 0),
            (loc != null ? loc.getBlockZ() : 0)
        ));
        OreReplaceUtil.tryReplaceNeighbors(event.getBlock(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block clicked = event.getClickedBlock();
        Player player = event.getPlayer();
        if (clicked == null || player == null) return;

        Block cacheTarget = null;
        Material clickedType = clicked.getType();
        if (clickedType == Material.RESPAWN_ANCHOR) {
            cacheTarget = clicked;
        } else if (clicked.getBlockData() instanceof Bed bedData) {
            cacheTarget = clicked;
            if (bedData.getPart() != Bed.Part.HEAD) {
                BlockFace facing = bedData.getFacing();
                cacheTarget = clicked.getRelative(facing);
            }
        } else {
            return;
        }

        long now = System.currentTimeMillis();
        BlockCacheKey key = BlockCacheKey.of(cacheTarget);

        CachedActor existing = blockInteractActorCache.get(key);
        if (existing != null && existing.expiresAtMillis > now) return;

        blockInteractActorCache.put(key, new CachedActor(player.getUniqueId(), now + INTERACT_CACHE_TTL_MILLIS));
        if (BlueOreReplacer.debug) {
            BlueOreReplacer.sendDebug(String.format(
                "互動快取寫入: §6%s §7@ §9%s §c%d §a%d §b%d §7TTL: §e%dms",
                player.getName(),
                cacheTarget.getWorld().getName(),
                cacheTarget.getX(),
                cacheTarget.getY(),
                cacheTarget.getZ(),
                INTERACT_CACHE_TTL_MILLIS
            ));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        Entity entity = event.getEntity();
        if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug(String.format(
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
        Player actor = null;
        if (entity instanceof TNTPrimed tnt) {
            Entity source = tnt.getSource();
            if (source instanceof Player player) {
                actor = player;
            } else if (source instanceof Projectile projectile) {
                ProjectileSource shooter = projectile.getShooter();
                if (shooter instanceof Player player) actor = player;
            }
        }

        List<Block> explodedBlocks = event.blockList();
        Set<Long> explodedBlockSet = new HashSet<>(explodedBlocks.size() * 2);

        if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug(String.format(
            "實體爆炸: §6%s §7@ §9%s §c%d §a%d §b%d §7影響數: §e%d §7來源: §6%s",
            (entity != null ? entity.getType().name() : "unknown"),
            (loc != null ? loc.getWorld().getName() : "unknown"),
            (loc != null ? loc.getBlockX() : 0),
            (loc != null ? loc.getBlockY() : 0),
            (loc != null ? loc.getBlockZ() : 0),
            explodedBlocks.size(),
            (actor != null ? actor.getName() : "unknown")
        ));

        for (Block block : explodedBlocks) {
            explodedBlockSet.add(encode(block.getX(), block.getY(), block.getZ()));
            OreReplaceUtil.tryReplace(block, null, false, actor);
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
            OreReplaceUtil.tryReplace(block, null, true, actor);
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
        Block sourceBlock = event.getBlock();
        Location loc = sourceBlock.getLocation();
        Player actor = null;
        long now = System.currentTimeMillis();
        BlockCacheKey blockKey = BlockCacheKey.of(sourceBlock);
        CachedActor cached = blockInteractActorCache.get(blockKey);
        if (cached != null) {
            if (cached.expiresAtMillis < now) blockInteractActorCache.remove(blockKey, cached);
            else actor = plugin.getServer().getPlayer(cached.actorUuid);
        }

        List<Block> explodedBlocks = event.blockList();
        Set<Long> explodedBlockSet = new HashSet<>(explodedBlocks.size() * 2);

        if (BlueOreReplacer.debug) {
            BlueOreReplacer.sendDebug(String.format(
                "方塊爆炸: §e%s §7@ §9%s §c%d §a%d §b%d §7影響數: §e%d §7來源: §6%s",
                sourceBlock.getType().name(),
                (loc != null ? loc.getWorld().getName() : "unknown"),
                (loc != null ? loc.getBlockX() : 0),
                (loc != null ? loc.getBlockY() : 0),
                (loc != null ? loc.getBlockZ() : 0),
                explodedBlocks.size(),
                (actor != null ? actor.getName() : "unknown")
            ));
        }

        for (Block block : explodedBlocks) {
            explodedBlockSet.add(encode(block.getX(), block.getY(), block.getZ()));
            OreReplaceUtil.tryReplace(block, null, false, actor);
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
                    if (!neighbor.getType().isAir()) {
                        outerEdge.add(neighbor);
                    }
                }
            }
        }

        for (Block block : outerEdge) {
            OreReplaceUtil.tryReplace(block, null, true, actor);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        event.getBlocks().forEach(block -> {
            Location loc = block.getLocation();
            if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug(String.format(
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
            if (BlueOreReplacer.debug) BlueOreReplacer.sendDebug(String.format(
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

    public void clearInteractActorCacheForChunk(UUID worldUuid, int chunkX, int chunkZ) {
        blockInteractActorCache.keySet().removeIf(key ->
            key.worldUuid().equals(worldUuid) && (key.x() >> 4) == chunkX && (key.z() >> 4) == chunkZ
        );
    }

    private long encode(int x, int y, int z) {
        return (((long)x & 0x3FFFFFFL) << 38) | (((long)z & 0x3FFFFFFL) << 12) | ((long)y & 0xFFFL);
    }

    private record BlockCacheKey(UUID worldUuid, int x, int y, int z) {
        private static BlockCacheKey of(Block block) {
            return new BlockCacheKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    private static final class CachedActor {
        private final UUID actorUuid;
        private final long expiresAtMillis;

        private CachedActor(UUID actorUuid, long expiresAtMillis) {
            this.actorUuid = actorUuid;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}

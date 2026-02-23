package net.mcblueice.blueorereplacer.tracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;

import net.mcblueice.blueorereplacer.BlueOreReplacer;
import net.mcblueice.blueorereplacer.tracker.BlockStateCache.CachedWindow;
import net.mcblueice.blueorereplacer.tracker.BlockStateCache.ChunkKey;
import net.mcblueice.blueorereplacer.utils.TaskScheduler;

public final class BlockStateTracker {

    private static final long CACHE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(1);

    private final BlueOreReplacer plugin;
    private final Logger logger;
    private final boolean foliaEnvironment;
    private final BlockStateStore stateStore;
    private final BlockStateCache cache;
    private final TaskScheduler.RepeatingTaskHandler flushTask;

    public BlockStateTracker(BlueOreReplacer plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.foliaEnvironment = detectFoliaEnvironment();
        this.stateStore = new BlockStateStore(plugin);
        this.cache = new BlockStateCache();

        long flushSeconds = plugin.getConfig().getLong("ChunkCache.SaveInterval", 30L);
        if (flushSeconds <= 0L) {
            this.flushTask = null;
        } else {
            long ticks = Math.max(20L, flushSeconds * 20L);
            this.flushTask = TaskScheduler.runRepeatingTask(plugin, this::flushRoutine, ticks, ticks);
        }
    }

    public void markModified(Block block) {
        if (block == null) return;
        BlockStateWindow window = loadWindow(block.getChunk());
        BlockStateWindowMapper.ensureWindowForY(window, block.getY(), logger);
        int index = BlockStateWindowMapper.toIndex(block.getX() & 0xF, block.getZ() & 0xF, block.getY(), window.baseY(), window.height());
        if (index < 0) return;
        if (!window.modifiedBits().get(index)) {
            window.modifiedBits().set(index);
            window.markDirty();
        }
    }

    public void markExposed(Block block) {
        if (block == null) return;
        BlockStateWindow window = loadWindow(block.getChunk());
        BlockStateWindowMapper.ensureWindowForY(window, block.getY(), logger);
        int index = BlockStateWindowMapper.toIndex(block.getX() & 0xF, block.getZ() & 0xF, block.getY(), window.baseY(), window.height());
        if (index < 0) return;
        if (!window.exposedBits().get(index)) {
            window.exposedBits().set(index);
            window.markDirty();
        }
    }

    public boolean isModified(Block block) {
        if (block == null) return false;
        BlockStateWindow window = loadWindow(block.getChunk());
        if (window.height() <= 0) return false;
        int index = BlockStateWindowMapper.toIndex(block.getX() & 0xF, block.getZ() & 0xF, block.getY(), window.baseY(), window.height());
        return index >= 0 && window.modifiedBits().get(index);
    }

    public boolean isExposed(Block block) {
        if (block == null) return false;
        BlockStateWindow window = loadWindow(block.getChunk());
        if (window.height() <= 0) return false;
        int index = BlockStateWindowMapper.toIndex(block.getX() & 0xF, block.getZ() & 0xF, block.getY(), window.baseY(), window.height());
        return index >= 0 && window.exposedBits().get(index);
    }

    public ChunkStats getChunkStats(Chunk chunk) {
        BlockStateWindow window = loadWindow(chunk);
        int capacity = window.height() * 16 * 16;
        int modified = window.modifiedBits().cardinality();
        double ratio = capacity == 0 ? 0D : (double) modified / (double) capacity;
        int bytes = window.modifiedBits().toByteArray().length;
        return new ChunkStats(window.baseY(), window.height(), modified, capacity, ratio, bytes);
    }

    public Set<NamespacedKey> getChunkPdcKeys(Chunk chunk) {
        return stateStore.getKeys(chunk);
    }

    public void clear(Chunk chunk) {
        if (chunk == null) return;
        stateStore.clear(chunk);
        cache.remove(ChunkKey.of(chunk));
    }

    public void flushAndInvalidate(Chunk chunk) {
        if (chunk == null) return;
        ChunkKey key = ChunkKey.of(chunk);
        CachedWindow cached = cache.remove(key);
        flushIfDirty(chunk, cached);
    }

    public void flushAll() {
        if (!foliaEnvironment || Bukkit.isPrimaryThread()) {
            cache.forEach((key, cached) -> {
                BlockStateWindow window = cached.window();
                if (!window.isDirty()) return;
                World world = Bukkit.getWorld(key.worldId());
                if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) return;
                Chunk chunk = world.getChunkAt(key.chunkX(), key.chunkZ());
                stateStore.save(chunk, window);
            });
            return;
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        cache.forEach((key, cached) -> {
            BlockStateWindow window = cached.window();
            if (!window.isDirty()) return;

            World world = Bukkit.getWorld(key.worldId());
            if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
                window.clearFlushPending();
                return;
            }

            if (!window.tryMarkFlushPending()) return;

            Location anchor = chunkAnchor(world, key, window);
            CompletableFuture<Void> future = new CompletableFuture<>();
            futures.add(future);

            TaskScheduler.runRegionTask(anchor, plugin, () -> {
                try {
                    Chunk chunk = world.getChunkAt(key.chunkX(), key.chunkZ());
                    stateStore.save(chunk, window);
                } finally {
                    future.complete(null);
                }
            });
        });

        for (CompletableFuture<Void> future : futures) {
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("[BlockStateTracker] flushAll interrupted while waiting for region tasks");
            } catch (Exception ex) {
                logger.warning("[BlockStateTracker] flushAll timed out or failed: " + ex.getMessage());
            }
        }
    }

    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        flushAll();
        cache.clear();
    }

    public record ChunkStats(int baseY, int height, int modified, int capacity, double ratio, int bytes) {
        public String ratioPercent() {
            return String.format("%.2f%%", ratio * 100.0);
        }
    }

    private BlockStateWindow loadWindow(Chunk chunk) {
        long now = System.currentTimeMillis();
        World world = chunk.getWorld();
        int worldMin = world.getMinHeight();
        int worldMax = world.getMaxHeight();
        ChunkKey key = ChunkKey.of(chunk);

        CachedWindow cached = cache.get(key);
        if (cached != null) {
            BlockStateWindow existing = cached.window();
            if (cached.isExpired(now) || !existing.matchesWorldBounds(worldMin, worldMax)) {
                flushIfDirty(chunk, cached);
                cache.remove(key, cached);
            } else {
                cached.renew(now + CACHE_TTL_MILLIS);
                return existing;
            }
        }

        BlockStateWindow loaded = stateStore.load(chunk, worldMin, worldMax);
        cache.put(key, new CachedWindow(loaded, now + CACHE_TTL_MILLIS));
        return loaded;
    }

    private void flushIfDirty(Chunk chunk, CachedWindow cached) {
        if (cached == null || chunk == null) return;
        BlockStateWindow window = cached.window();
        if (window.isDirty()) {
            stateStore.save(chunk, window);
        }
    }

    private void flushRoutine() {
        long now = System.currentTimeMillis();
        cache.forEach((key, cached) -> {
            BlockStateWindow window = cached.window();

            if (window.isDirty()) {
                World world = Bukkit.getWorld(key.worldId());
                if (world != null && world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
                    if (foliaEnvironment) {
                        if (window.tryMarkFlushPending()) {
                            Location anchor = chunkAnchor(world, key, window);
                            TaskScheduler.runRegionTask(anchor, plugin, () -> {
                                Chunk chunk = world.getChunkAt(key.chunkX(), key.chunkZ());
                                stateStore.save(chunk, window);
                            });
                        }
                    } else {
                        Chunk chunk = world.getChunkAt(key.chunkX(), key.chunkZ());
                        stateStore.save(chunk, window);
                    }
                } else {
                    window.clearFlushPending();
                }
            }

            if (cached.isExpired(now) && !window.isDirty() && !window.isFlushPending()) {
                cache.remove(key, cached);
            }
        });
    }

    private boolean detectFoliaEnvironment() {
        try {
            return Bukkit.getRegionScheduler() != null;
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            return false;
        }
    }

    private Location chunkAnchor(World world, ChunkKey key, BlockStateWindow window) {
        int minY = world.getMinHeight();
        int maxY = Math.max(minY, world.getMaxHeight() - 1);
        int baseY = window.height() > 0 ? window.baseY() : minY;
        int clampedY = Math.max(minY, Math.min(maxY, baseY));
        double x = (key.chunkX() << 4) + 8.0;
        double z = (key.chunkZ() << 4) + 8.0;
        return new Location(world, x, clampedY, z);
    }
}

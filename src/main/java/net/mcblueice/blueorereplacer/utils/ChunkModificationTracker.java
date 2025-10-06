package net.mcblueice.blueorereplacer.utils;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.mcblueice.blueorereplacer.BlueOreReplacer;


public class ChunkModificationTracker {

    private static final String KEY_BITS = "modified_bits"; // byte[]
    private static final String KEY_BASE_Y = "modified_baseY"; // int
    private static final String KEY_HEIGHT = "modified_height"; // int
    private static final long CACHE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(1);

    private final NamespacedKey bitsKey;
    private final NamespacedKey baseYKey;
    private final NamespacedKey heightKey;
    private final Logger logger;
    private final BlueOreReplacer plugin;
    private final boolean foliaEnvironment;
    private final Map<ChunkKey, CachedContext> cache = new ConcurrentHashMap<>();
    private final TaskScheduler.RepeatingTaskHandler flushTask;

    public ChunkModificationTracker(BlueOreReplacer plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.bitsKey = new NamespacedKey(plugin, KEY_BITS);
        this.baseYKey = new NamespacedKey(plugin, KEY_BASE_Y);
        this.heightKey = new NamespacedKey(plugin, KEY_HEIGHT);
        this.foliaEnvironment = detectFoliaEnvironment();

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
        Chunk chunk = block.getChunk();
        Context ctx = loadContext(chunk);
        ensureWindowForY(ctx, block.getY());
        int index = toIndex(block.getX() & 0xF, block.getZ() & 0xF, block.getY(), ctx.baseY, ctx.height);
        if (index < 0) return;
        if (!ctx.bitSet.get(index)) {
            ctx.bitSet.set(index);
            ctx.markDirty();
        }
    }

    public boolean isModified(Block block) {
        Chunk chunk = block.getChunk();
        Context ctx = loadContext(chunk);
        if (ctx.height <= 0) return false;
        int index = toIndex(block.getX() & 0xF, block.getZ() & 0xF, block.getY(), ctx.baseY, ctx.height);
        return index >= 0 && ctx.bitSet.get(index);
    }

    private int toIndex(int x, int z, int y, int baseY, int height) {
        int relY = y - baseY;
        if (relY < 0 || relY >= height) return -1;
        return (relY * 16 + z) * 16 + x; // (relY * 256) + (z * 16) + x
    }

    private static class Context {
        BitSet bitSet;
        int baseY;
        int height;
        int worldMin;
        int worldMax;
        volatile boolean dirty;
        final AtomicBoolean flushPending = new AtomicBoolean(false);
        // capacity = height * 16 * 16

        void markDirty() {
            this.dirty = true;
        }

        boolean isDirty() {
            return this.dirty;
        }

        void clearDirty() {
            this.dirty = false;
        }

        boolean tryMarkFlushPending() {
            return this.flushPending.compareAndSet(false, true);
        }

        void clearFlushPending() {
            this.flushPending.set(false);
        }

        boolean isFlushPending() {
            return this.flushPending.get();
        }

        void setWorldBounds(int min, int max) {
            this.worldMin = min;
            this.worldMax = max;
        }

        boolean matchesWorldBounds(int min, int max) {
            return this.worldMin == min && this.worldMax == max;
        }
    }

    private Context loadContext(Chunk chunk) {
        long now = System.currentTimeMillis();
        World world = chunk.getWorld();
        int worldMin = world.getMinHeight();
        int worldMax = world.getMaxHeight();
        ChunkKey key = ChunkKey.of(chunk);

        CachedContext cached = cache.get(key);
        if (cached != null) {
            Context existing = cached.context;
            if (cached.isExpired(now) || !existing.matchesWorldBounds(worldMin, worldMax)) {
                flushIfDirty(chunk, cached);
                cache.remove(key, cached);
            } else {
                cached.renew(now + CACHE_TTL_MILLIS);
                return existing;
            }
        }

        Context ctx = loadFromPersistentData(chunk, worldMin, worldMax);
        CachedContext newCached = new CachedContext(ctx, now + CACHE_TTL_MILLIS);
        cache.put(key, newCached);
        return ctx;
    }

    private Context loadFromPersistentData(Chunk chunk, int worldMin, int worldMax) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        Integer storedBaseY = pdc.get(baseYKey, PersistentDataType.INTEGER);
        Integer storedHeight = pdc.get(heightKey, PersistentDataType.INTEGER);
        byte[] raw = pdc.get(bitsKey, PersistentDataType.BYTE_ARRAY);

        Context ctx = new Context();
        ctx.setWorldBounds(worldMin, worldMax);

        if (raw != null && storedBaseY != null && storedHeight != null) {
            BitSet storedBits = BitSet.valueOf(raw);
            int storedTopExclusive = storedBaseY + storedHeight;

            int overlapBase = Math.max(storedBaseY, worldMin);
            int overlapTop = Math.min(storedTopExclusive, worldMax);

            if (overlapTop > overlapBase) {
                int overlapHeight = overlapTop - overlapBase;
                ctx.baseY = overlapBase;
                ctx.height = overlapHeight;
                ctx.bitSet = new BitSet(overlapHeight * 16 * 16);

                int baseShiftLayers = overlapBase - storedBaseY;
                for (int bit = storedBits.nextSetBit(0); bit >= 0; bit = storedBits.nextSetBit(bit + 1)) {
                    int relLayer = bit / 256;
                    int inLayer = bit % 256;
                    int worldY = storedBaseY + relLayer;
                    if (worldY >= overlapBase && worldY < overlapTop) {
                        int newRelLayer = relLayer - baseShiftLayers;
                        int newIndex = newRelLayer * 256 + inLayer;
                        ctx.bitSet.set(newIndex);
                    }
                }

                if (baseShiftLayers != 0 || overlapHeight != storedHeight) {
                    ctx.markDirty();
                }

                logger.fine(() -> String.format(
                    "[ChunkModificationTracker] Remapped chunk (%d,%d) window to baseY=%d height=%d (from stored baseY=%d height=%d)",
                    chunk.getX(), chunk.getZ(), ctx.baseY, ctx.height, storedBaseY, storedHeight
                ));
            } else {
                ctx.baseY = worldMin;
                ctx.height = 0;
                ctx.bitSet = new BitSet();
                ctx.markDirty();
                logger.fine(() -> String.format(
                    "[ChunkModificationTracker] No overlap with world height; cleared window for chunk (%d,%d)",
                    chunk.getX(), chunk.getZ()
                ));
            }
        } else {
            ctx.baseY = worldMin;
            ctx.height = 0;
            ctx.bitSet = new BitSet();
            logger.finer(() -> String.format(
                "[ChunkModificationTracker] Initialized empty window for chunk (%d,%d) baseY=%d",
                chunk.getX(), chunk.getZ(), ctx.baseY
            ));
        }

        return ctx;
    }

    private void saveContext(Chunk chunk, Context ctx) {
        if (!ctx.isDirty()) {
            ctx.clearFlushPending();
            return;
        }
        try {
            PersistentDataContainer pdc = chunk.getPersistentDataContainer();
            World world = chunk.getWorld();
            ctx.setWorldBounds(world.getMinHeight(), world.getMaxHeight());
            pdc.set(baseYKey, PersistentDataType.INTEGER, ctx.baseY);
            pdc.set(heightKey, PersistentDataType.INTEGER, ctx.height);
            pdc.set(bitsKey, PersistentDataType.BYTE_ARRAY, ctx.bitSet.toByteArray());
            ctx.clearDirty();
        } finally {
            ctx.clearFlushPending();
        }
    }

    public ChunkStats getChunkStats(Chunk chunk) {
        Context ctx = loadContext(chunk);
        int capacity = ctx.height * 16 * 16;
        int modified = ctx.bitSet.cardinality();
        double ratio = capacity == 0 ? 0D : (double) modified / (double) capacity;
        int bytes = ctx.bitSet.toByteArray().length;
        return new ChunkStats(ctx.baseY, ctx.height, modified, capacity, ratio, bytes);
    }

    public Set<NamespacedKey> getChunkPdcKeys(Chunk chunk) {
        return chunk.getPersistentDataContainer().getKeys();
    }

    public record ChunkStats(int baseY, int height, int modified, int capacity, double ratio, int bytes) {
        public String ratioPercent() { return String.format("%.2f%%", ratio * 100.0); }
    }

    public void clear(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        pdc.remove(bitsKey);
        pdc.remove(baseYKey);
        pdc.remove(heightKey);
        cache.remove(ChunkKey.of(chunk));
    }

    public void flushAndInvalidate(Chunk chunk) {
        if (chunk == null) return;
        ChunkKey key = ChunkKey.of(chunk);
        CachedContext cached = cache.remove(key);
        flushIfDirty(chunk, cached);
    }

    public void flushAll() {
        if (!foliaEnvironment || Bukkit.isPrimaryThread()) {
            cache.forEach((key, cached) -> {
                Context ctx = cached.context;
                if (!ctx.isDirty()) return;
                World world = Bukkit.getWorld(key.worldId());
                if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) return;
                Chunk chunk = world.getChunkAt(key.chunkX(), key.chunkZ());
                saveContext(chunk, ctx);
            });
            return;
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        cache.forEach((key, cached) -> {
            Context ctx = cached.context;
            if (!ctx.isDirty()) return;
            World world = Bukkit.getWorld(key.worldId());
            if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
                ctx.clearFlushPending();
                return;
            }
            if (!ctx.tryMarkFlushPending()) return;
            Location anchor = chunkAnchor(world, key, ctx);
            CompletableFuture<Void> future = new CompletableFuture<>();
            futures.add(future);
            TaskScheduler.runRegionTask(anchor, plugin, () -> {
                try {
                    Chunk chunk = world.getChunkAt(key.chunkX(), key.chunkZ());
                    saveContext(chunk, ctx);
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
                logger.warning("[ChunkModificationTracker] flushAll interrupted while waiting for region tasks");
            } catch (Exception ex) {
                logger.warning("[ChunkModificationTracker] flushAll timed out or failed: " + ex.getMessage());
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

    private void ensureWindowForY(Context ctx, int y) {
        if (ctx.bitSet == null) {
            ctx.bitSet = new BitSet();
        }
        if (ctx.height <= 0) {
            ctx.baseY = y;
            ctx.height = 1;
            ctx.bitSet = new BitSet(256);
            ctx.markDirty();
            return;
        }

        int topExclusive = ctx.baseY + ctx.height;
        if (y >= ctx.baseY && y < topExclusive) return;

        if (y < ctx.baseY) {
            int deltaLayers = ctx.baseY - y;
            int newHeight = ctx.height + deltaLayers;
            BitSet newBits = new BitSet(newHeight * 256);

            for (int bit = ctx.bitSet.nextSetBit(0); bit >= 0; bit = ctx.bitSet.nextSetBit(bit + 1)) {
                int relLayer = bit / 256;
                int inLayer = bit % 256;
                int newRelLayer = relLayer + deltaLayers;
                int newIndex = newRelLayer * 256 + inLayer;
                newBits.set(newIndex);
            }

            ctx.baseY = y;
            ctx.height = newHeight;
            ctx.bitSet = newBits;
            ctx.markDirty();
            logger.fine(() -> String.format(
                "[ChunkModificationTracker] Expanded window downward to baseY=%d height=%d", ctx.baseY, ctx.height
            ));
        } else {
            ctx.height = (y - ctx.baseY) + 1;
            ctx.markDirty();
            logger.finer(() -> String.format(
                "[ChunkModificationTracker] Expanded window upward to baseY=%d height=%d", ctx.baseY, ctx.height
            ));
        }
    }

    private void flushRoutine() {
        long now = System.currentTimeMillis();
        cache.forEach((key, cached) -> {
            Context ctx = cached.context;
            if (ctx.isDirty()) {
                World world = Bukkit.getWorld(key.worldId());
                if (world != null && world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
                    if (foliaEnvironment) {
                        if (ctx.tryMarkFlushPending()) {
                            Location anchor = chunkAnchor(world, key, ctx);
                            TaskScheduler.runRegionTask(anchor, plugin, () -> {
                                Chunk chunk = world.getChunkAt(key.chunkX(), key.chunkZ());
                                saveContext(chunk, ctx);
                            });
                        }
                    } else {
                        Chunk chunk = world.getChunkAt(key.chunkX(), key.chunkZ());
                        saveContext(chunk, ctx);
                    }
                } else {
                    ctx.clearFlushPending();
                }
            }
            if (cached.isExpired(now) && !ctx.isDirty() && !ctx.isFlushPending()) {
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

    private Location chunkAnchor(World world, ChunkKey key, Context ctx) {
        int minY = world.getMinHeight();
        int maxY = Math.max(minY, world.getMaxHeight() - 1);
        int baseY = ctx.height > 0 ? ctx.baseY : minY;
        int clampedY = Math.max(minY, Math.min(maxY, baseY));
        double x = (key.chunkX() << 4) + 8.0;
        double z = (key.chunkZ() << 4) + 8.0;
        return new Location(world, x, clampedY, z);
    }

    private void flushIfDirty(Chunk chunk, CachedContext cached) {
        if (cached == null || chunk == null) return;
        Context ctx = cached.context;
        if (ctx.isDirty()) {
            saveContext(chunk, ctx);
        }
    }

    private static class CachedContext {
        final Context context;
        volatile long expiresAt;

        CachedContext(Context context, long expiresAt) {
            this.context = context;
            this.expiresAt = expiresAt;
        }

        void renew(long newExpiry) {
            this.expiresAt = newExpiry;
        }

        boolean isExpired(long now) {
            return now > this.expiresAt;
        }
    }

    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {
        static ChunkKey of(Chunk chunk) {
            return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        }
    }
}

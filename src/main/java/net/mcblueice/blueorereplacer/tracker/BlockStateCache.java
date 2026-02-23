package net.mcblueice.blueorereplacer.tracker;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.bukkit.Chunk;

public final class BlockStateCache {
    private final Map<ChunkKey, CachedWindow> cache = new ConcurrentHashMap<>();

    public CachedWindow get(ChunkKey key) {
        return cache.get(key);
    }

    public void put(ChunkKey key, CachedWindow value) {
        cache.put(key, value);
    }

    public CachedWindow remove(ChunkKey key) {
        return cache.remove(key);
    }

    public void remove(ChunkKey key, CachedWindow cachedWindow) {
        cache.remove(key, cachedWindow);
    }

    public void clear() {
        cache.clear();
    }

    public void forEach(BiConsumer<ChunkKey, CachedWindow> consumer) {
        cache.forEach(consumer);
    }

    public record ChunkKey(UUID worldId, int chunkX, int chunkZ) {
        public static ChunkKey of(Chunk chunk) {
            return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        }
    }

    public static final class CachedWindow {
        private final BlockStateWindow window;
        private volatile long expiresAt;

        public CachedWindow(BlockStateWindow window, long expiresAt) {
            this.window = window;
            this.expiresAt = expiresAt;
        }

        public BlockStateWindow window() {
            return window;
        }

        public void renew(long newExpiry) {
            this.expiresAt = newExpiry;
        }

        public boolean isExpired(long now) {
            return now > this.expiresAt;
        }
    }
}

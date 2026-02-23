package net.mcblueice.blueorereplacer.tracker;

import java.util.BitSet;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class BlockStateStore {

    private static final String KEY_MODIFIED_BITS = "modified_bits";
    private static final String KEY_EXPOSED_BITS = "exposed_bits";
    private static final String KEY_BASE_Y = "modified_baseY";
    private static final String KEY_HEIGHT = "modified_height";

    private final NamespacedKey modifiedBitsKey;
    private final NamespacedKey exposedBitsKey;
    private final NamespacedKey baseYKey;
    private final NamespacedKey heightKey;
    private final Logger logger;

    public BlockStateStore(JavaPlugin plugin) {
        this.modifiedBitsKey = new NamespacedKey(plugin, KEY_MODIFIED_BITS);
        this.exposedBitsKey = new NamespacedKey(plugin, KEY_EXPOSED_BITS);
        this.baseYKey = new NamespacedKey(plugin, KEY_BASE_Y);
        this.heightKey = new NamespacedKey(plugin, KEY_HEIGHT);
        this.logger = plugin.getLogger();
    }

    public BlockStateWindow load(Chunk chunk, int worldMin, int worldMax) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();

        Integer storedBaseY = pdc.get(baseYKey, PersistentDataType.INTEGER);
        Integer storedHeight = pdc.get(heightKey, PersistentDataType.INTEGER);
        byte[] rawModified = pdc.get(modifiedBitsKey, PersistentDataType.BYTE_ARRAY);
        byte[] rawExposed = pdc.get(exposedBitsKey, PersistentDataType.BYTE_ARRAY);

        boolean hasAnyBits = rawModified != null || rawExposed != null;
        if (hasAnyBits && storedBaseY != null && storedHeight != null) {
            BitSet modifiedBits = (rawModified == null) ? new BitSet() : BitSet.valueOf(rawModified);
            BitSet exposedBits = (rawExposed == null) ? new BitSet() : BitSet.valueOf(rawExposed);
            return BlockStateWindowMapper.remapFromStored(
                    modifiedBits,
                    exposedBits,
                    storedBaseY,
                    storedHeight,
                    worldMin,
                    worldMax,
                    chunk.getX(),
                    chunk.getZ(),
                    logger
            );
        }

        BlockStateWindow empty = BlockStateWindow.empty(worldMin, worldMax);
        logger.finer(() -> String.format(
                "[BlockStateTracker] Initialized empty window for chunk (%d,%d) baseY=%d",
                chunk.getX(), chunk.getZ(), empty.baseY()
        ));
        return empty;
    }

    public void save(Chunk chunk, BlockStateWindow window) {
        if (!window.isDirty()) {
            window.clearFlushPending();
            return;
        }

        try {
            World world = chunk.getWorld();
            window.setWorldBounds(world.getMinHeight(), world.getMaxHeight());

            PersistentDataContainer pdc = chunk.getPersistentDataContainer();
            pdc.set(baseYKey, PersistentDataType.INTEGER, window.baseY());
            pdc.set(heightKey, PersistentDataType.INTEGER, window.height());
            pdc.set(modifiedBitsKey, PersistentDataType.BYTE_ARRAY, window.modifiedBits().toByteArray());
            pdc.set(exposedBitsKey, PersistentDataType.BYTE_ARRAY, window.exposedBits().toByteArray());

            window.clearDirty();
        } finally {
            window.clearFlushPending();
        }
    }

    public void clear(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        pdc.remove(modifiedBitsKey);
        pdc.remove(exposedBitsKey);
        pdc.remove(baseYKey);
        pdc.remove(heightKey);
    }

    public Set<NamespacedKey> getKeys(Chunk chunk) {
        return chunk.getPersistentDataContainer().getKeys();
    }
}

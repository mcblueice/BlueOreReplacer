package net.mcblueice.blueorereplacer.tracker;

import java.util.BitSet;
import java.util.logging.Logger;

public final class BlockStateWindowMapper {
    private BlockStateWindowMapper() {
    }

    public static int toIndex(int x, int z, int y, int baseY, int height) {
        int relativeY = y - baseY;
        if (relativeY < 0 || relativeY >= height) return -1;
        return (relativeY * 16 + z) * 16 + x;
    }

    public static void ensureWindowForY(BlockStateWindow window, int y, Logger logger) {
        if (window.modifiedBits() == null) {
            window.setModifiedBits(new BitSet());
        }
        if (window.exposedBits() == null) {
            window.setExposedBits(new BitSet());
        }

        if (window.height() <= 0) {
            window.setBaseY(y);
            window.setHeight(1);
            window.setModifiedBits(new BitSet(256));
            window.setExposedBits(new BitSet(256));
            window.markDirty();
            return;
        }

        int topExclusive = window.baseY() + window.height();
        if (y >= window.baseY() && y < topExclusive) return;

        if (y < window.baseY()) {
            int deltaLayers = window.baseY() - y;
            int newHeight = window.height() + deltaLayers;
            BitSet newBits = new BitSet(newHeight * 256);
            BitSet newExposedBits = new BitSet(newHeight * 256);

            for (int bit = window.modifiedBits().nextSetBit(0); bit >= 0; bit = window.modifiedBits().nextSetBit(bit + 1)) {
                int relativeLayer = bit / 256;
                int inLayer = bit % 256;
                int newRelativeLayer = relativeLayer + deltaLayers;
                int newIndex = newRelativeLayer * 256 + inLayer;
                newBits.set(newIndex);
            }

            for (int bit = window.exposedBits().nextSetBit(0); bit >= 0; bit = window.exposedBits().nextSetBit(bit + 1)) {
                int relativeLayer = bit / 256;
                int inLayer = bit % 256;
                int newRelativeLayer = relativeLayer + deltaLayers;
                int newIndex = newRelativeLayer * 256 + inLayer;
                newExposedBits.set(newIndex);
            }

            window.setBaseY(y);
            window.setHeight(newHeight);
            window.setModifiedBits(newBits);
            window.setExposedBits(newExposedBits);
            window.markDirty();

            if (logger != null) {
                logger.fine(() -> String.format(
                        "[BlockStateTracker] Expanded window downward to baseY=%d height=%d",
                        window.baseY(), window.height()
                ));
            }
            return;
        }

        window.setHeight((y - window.baseY()) + 1);
        window.markDirty();
        if (logger != null) {
            logger.finer(() -> String.format(
                    "[BlockStateTracker] Expanded window upward to baseY=%d height=%d",
                    window.baseY(), window.height()
            ));
        }
    }

    public static BlockStateWindow remapFromStored(
            BitSet storedBits,
            BitSet storedExposedBits,
            int storedBaseY,
            int storedHeight,
            int worldMin,
            int worldMax,
            int chunkX,
            int chunkZ,
            Logger logger
    ) {
        int storedTopExclusive = storedBaseY + storedHeight;
        int overlapBase = Math.max(storedBaseY, worldMin);
        int overlapTop = Math.min(storedTopExclusive, worldMax);

        if (overlapTop <= overlapBase) {
            BlockStateWindow window = BlockStateWindow.empty(worldMin, worldMax);
            window.markDirty();
            if (logger != null) {
                logger.fine(() -> String.format(
                        "[BlockStateTracker] No overlap with world height; cleared window for chunk (%d,%d)",
                        chunkX, chunkZ
                ));
            }
            return window;
        }

        int overlapHeight = overlapTop - overlapBase;
        BitSet remappedModified = new BitSet(overlapHeight * 16 * 16);
        BitSet remappedExposed = new BitSet(overlapHeight * 16 * 16);

        int baseShiftLayers = overlapBase - storedBaseY;
        for (int bit = storedBits.nextSetBit(0); bit >= 0; bit = storedBits.nextSetBit(bit + 1)) {
            int relativeLayer = bit / 256;
            int inLayer = bit % 256;
            int worldY = storedBaseY + relativeLayer;
            if (worldY >= overlapBase && worldY < overlapTop) {
                int newRelativeLayer = relativeLayer - baseShiftLayers;
                int newIndex = newRelativeLayer * 256 + inLayer;
                remappedModified.set(newIndex);
            }
        }

        for (int bit = storedExposedBits.nextSetBit(0); bit >= 0; bit = storedExposedBits.nextSetBit(bit + 1)) {
            int relativeLayer = bit / 256;
            int inLayer = bit % 256;
            int worldY = storedBaseY + relativeLayer;
            if (worldY >= overlapBase && worldY < overlapTop) {
                int newRelativeLayer = relativeLayer - baseShiftLayers;
                int newIndex = newRelativeLayer * 256 + inLayer;
                remappedExposed.set(newIndex);
            }
        }

        boolean dirty = baseShiftLayers != 0 || overlapHeight != storedHeight;
        if (logger != null) {
            logger.fine(() -> String.format(
                    "[BlockStateTracker] Remapped chunk (%d,%d) window to baseY=%d height=%d (from stored baseY=%d height=%d)",
                    chunkX, chunkZ, overlapBase, overlapHeight, storedBaseY, storedHeight
            ));
        }

        return new BlockStateWindow(remappedModified, remappedExposed, overlapBase, overlapHeight, worldMin, worldMax, dirty);
    }
}

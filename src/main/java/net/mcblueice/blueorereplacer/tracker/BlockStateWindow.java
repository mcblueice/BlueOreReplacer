package net.mcblueice.blueorereplacer.tracker;

import java.util.BitSet;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BlockStateWindow {
    private BitSet modifiedBits;
    private BitSet exposedBits;
    private int baseY;
    private int height;
    private int worldMin;
    private int worldMax;
    private volatile boolean dirty;
    private final AtomicBoolean flushPending = new AtomicBoolean(false);

    public BlockStateWindow(BitSet modifiedBits, BitSet exposedBits, int baseY, int height, int worldMin, int worldMax, boolean dirty) {
        this.modifiedBits = modifiedBits;
        this.exposedBits = exposedBits;
        this.baseY = baseY;
        this.height = height;
        this.worldMin = worldMin;
        this.worldMax = worldMax;
        this.dirty = dirty;
    }

    public static BlockStateWindow empty(int worldMin, int worldMax) {
        return new BlockStateWindow(new BitSet(), new BitSet(), worldMin, 0, worldMin, worldMax, false);
    }

    public BitSet modifiedBits() {
        return modifiedBits;
    }

    public void setModifiedBits(BitSet modifiedBits) {
        this.modifiedBits = modifiedBits;
    }

    public BitSet exposedBits() {
        return exposedBits;
    }

    public void setExposedBits(BitSet exposedBits) {
        this.exposedBits = exposedBits;
    }

    public int baseY() {
        return baseY;
    }

    public void setBaseY(int baseY) {
        this.baseY = baseY;
    }

    public int height() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setWorldBounds(int min, int max) {
        this.worldMin = min;
        this.worldMax = max;
    }

    public boolean matchesWorldBounds(int min, int max) {
        return this.worldMin == min && this.worldMax == max;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public boolean tryMarkFlushPending() {
        return this.flushPending.compareAndSet(false, true);
    }

    public void clearFlushPending() {
        this.flushPending.set(false);
    }

    public boolean isFlushPending() {
        return this.flushPending.get();
    }
}

package net.mcblueice.blueorereplacer.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

import net.mcblueice.blueorereplacer.BlueOreReplacer;

public class ChunkListener implements Listener {

    private final BlueOreReplacer plugin;

    public ChunkListener(BlueOreReplacer plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        plugin.getBlockTracker().flushAndInvalidate(event.getChunk());
        plugin.getBlockChangeListener().clearInteractActorCacheForChunk(
            event.getWorld().getUID(),
            event.getChunk().getX(),
            event.getChunk().getZ()
        );
    }
}

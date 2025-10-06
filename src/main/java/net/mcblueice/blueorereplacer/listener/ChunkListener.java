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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        plugin.getChunkTracker().flushAndInvalidate(event.getChunk());
    }
}

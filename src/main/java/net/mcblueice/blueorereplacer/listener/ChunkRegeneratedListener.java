package net.mcblueice.blueorereplacer.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.mcblueice.blueorereplacer.BlueOreReplacer;
import net.mcblueice.bluerevive.api.events.ChunkRegeneratedEvent;

public class ChunkRegeneratedListener implements Listener {

    private final BlueOreReplacer plugin;

    public ChunkRegeneratedListener(BlueOreReplacer plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkRegenerated(ChunkRegeneratedEvent event) {
        plugin.getBlockTracker().clear(event.getChunk());
    }
}

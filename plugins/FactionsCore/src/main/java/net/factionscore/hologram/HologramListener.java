package net.factionscore.hologram;

import org.powernukkitx.Server;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.player.PlayerJoinEvent;
import org.powernukkitx.plugin.Plugin;

public final class HologramListener implements Listener {

    private final HologramManager holograms;
    private final Plugin plugin;

    public HologramListener(HologramManager holograms, Plugin plugin) {
        this.holograms = holograms;
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        // Delay so the client has finished its spawn sequence before receiving the fake entities.
        Server.getInstance().getScheduler().scheduleDelayedTask(plugin, () -> {
            if (player.isOnline()) {
                holograms.sendAll(player);
            }
        }, 60);
    }
}

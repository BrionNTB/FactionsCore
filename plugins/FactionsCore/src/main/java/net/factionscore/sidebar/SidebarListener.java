package net.factionscore.sidebar;

import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.player.PlayerQuitEvent;

public final class SidebarListener implements Listener {

    private final SidebarManager sidebar;

    public SidebarListener(SidebarManager sidebar) {
        this.sidebar = sidebar;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sidebar.forget(event.getPlayer());
    }
}

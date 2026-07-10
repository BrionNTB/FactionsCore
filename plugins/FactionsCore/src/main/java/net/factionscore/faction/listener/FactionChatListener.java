package net.factionscore.faction.listener;

import net.factionscore.faction.Faction;
import net.factionscore.faction.FactionManager;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.EventPriority;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.player.PlayerChatEvent;
import org.powernukkitx.utils.TextFormat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Faction-only chat (/f chat toggle) plus a faction-name prefix on public chat, the standard
 * factions chat setup. Uses PlayerChatEvent's recipient set rather than cancel-and-resend, so
 * other chat plugins still see a normal chat event.
 */
public final class FactionChatListener implements Listener {

    private final FactionManager factions;
    private final Set<UUID> factionChatToggled = ConcurrentHashMap.newKeySet();

    public FactionChatListener(FactionManager factions) {
        this.factions = factions;
    }

    /** @return the new toggle state. */
    public boolean toggle(UUID player) {
        if (factionChatToggled.remove(player)) {
            return false;
        }
        factionChatToggled.add(player);
        return true;
    }

    public void forget(UUID player) {
        factionChatToggled.remove(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        Faction faction = factions.getByMember(player.getUniqueId()).orElse(null);

        if (faction != null && factionChatToggled.contains(player.getUniqueId())) {
            Set<CommandSender> recipients = new HashSet<>();
            for (Player online : Server.getInstance().getOnlinePlayers().values()) {
                if (faction.isMember(online.getUniqueId())) {
                    recipients.add(online);
                }
            }
            event.setRecipients(recipients);
            event.setMessage(TextFormat.DARK_GREEN + "[F] " + TextFormat.GREEN + event.getMessage());
            return;
        }

        if (faction != null) {
            event.setMessage(TextFormat.GRAY + "[" + TextFormat.GOLD + faction.getName() + TextFormat.GRAY + "] "
                    + TextFormat.WHITE + event.getMessage());
        }
    }
}

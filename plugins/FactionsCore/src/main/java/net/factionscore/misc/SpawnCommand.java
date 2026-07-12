package net.factionscore.misc;

import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.utils.TextFormat;

public final class SpawnCommand extends Command {

    private final WarmupManager warmups;

    public SpawnCommand(WarmupManager warmups) {
        super("spawn", "Teleport to spawn", "/spawn");
        this.setPermission("factionscore.command.spawn");
        this.warmups = warmups;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "Only players can use /spawn.");
            return true;
        }
        warmups.start(player, "Spawn", () -> {
            if (!player.isOnline()) return;
            player.teleport(Server.getInstance().getDefaultLevel().getSafeSpawn());
            player.sendMessage(TextFormat.GREEN + "Welcome back to spawn!");
        });
        return true;
    }
}

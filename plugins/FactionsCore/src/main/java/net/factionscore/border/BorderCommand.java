package net.factionscore.border;

import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.utils.TextFormat;

public final class BorderCommand extends Command {

    private final WorldBorderManager border;

    public BorderCommand(WorldBorderManager border) {
        super("worldborder", "World border inspection/administration", "/worldborder help", new String[]{"wb", "border"});
        this.setPermission("factionscore.command.worldborder");
        this.border = border;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("info") && sender instanceof Player player) {
            var level = player.getLevel();
            double radius = border.radiusFor(level);
            double distance = border.distancePastBorder(level, player.x, player.z);
            sender.sendMessage(TextFormat.GOLD + "--- World border ---");
            sender.sendMessage(TextFormat.YELLOW + "Radius here: " + (int) radius + " blocks");
            sender.sendMessage(TextFormat.YELLOW + (distance > 0
                    ? "You are " + (int) distance + " blocks past the border!"
                    : "You are " + (int) -distance + " blocks from the border."));
            return true;
        }
        sender.sendMessage(TextFormat.YELLOW + "/worldborder info");
        return true;
    }
}

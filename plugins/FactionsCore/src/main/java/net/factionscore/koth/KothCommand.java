package net.factionscore.koth;

import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.utils.TextFormat;

public final class KothCommand extends Command {

    private final KothManager koth;

    public KothCommand(KothManager koth) {
        super("koth", "King of the Hill event", "/koth status");
        this.setPermission("factionscore.command.koth");
        this.koth = koth;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "status";
        switch (sub) {
            case "set" -> set(sender, args);
            case "start" -> {
                if (!sender.hasPermission("factionscore.command.kothadmin")) {
                    sender.sendMessage(TextFormat.RED + "No permission.");
                    return true;
                }
                if (!koth.start()) {
                    sender.sendMessage(TextFormat.RED + (koth.isActive()
                            ? "A KOTH event is already running."
                            : "No KOTH zone set yet -- stand in it and run /koth set <radius>."));
                }
            }
            case "stop" -> {
                if (!sender.hasPermission("factionscore.command.kothadmin")) {
                    sender.sendMessage(TextFormat.RED + "No permission.");
                    return true;
                }
                koth.stop(true);
            }
            default -> sender.sendMessage(TextFormat.GOLD + "[KOTH] " + TextFormat.YELLOW + koth.status());
        }
        return true;
    }

    private void set(CommandSender sender, String[] args) {
        if (!sender.hasPermission("factionscore.command.kothadmin")) {
            sender.sendMessage(TextFormat.RED + "No permission.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "Stand at the hill's center and run this in-game.");
            return;
        }
        double radius = 5;
        if (args.length >= 2) {
            try {
                radius = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(TextFormat.RED + "Invalid radius.");
                return;
            }
        }
        koth.setZone(player.getLevel(), player.x, player.y, player.z, radius);
        sender.sendMessage(TextFormat.GREEN + "KOTH zone set here with radius " + radius + ".");
    }
}

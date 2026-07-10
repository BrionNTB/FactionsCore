package net.factionscore.envoy;

import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.utils.TextFormat;

public final class EnvoyCommand extends Command {

    private final EnvoyManager envoys;

    public EnvoyCommand(EnvoyManager envoys) {
        super("envoy", "Supply-drop event", "/envoy <start|setcenter|clear>");
        this.setPermission("factionscore.command.envoy");
        this.envoys = envoys;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "help";
        switch (sub) {
            case "start" -> {
                int dropped = envoys.start();
                if (dropped > 0) {
                    sender.sendMessage(TextFormat.GREEN + "Envoy started with " + dropped + " drops.");
                } else if (dropped == 0) {
                    sender.sendMessage(TextFormat.RED + "Couldn't find valid drop spots -- is the center over generated terrain?");
                } else {
                    sender.sendMessage(TextFormat.RED + (envoys.hasCenter()
                            ? "An envoy is already active -- /envoy clear first."
                            : "No envoy center set. Stand at the warzone center and run /envoy setcenter <radius>."));
                }
            }
            case "setcenter" -> setCenter(sender, args);
            case "clear" -> {
                envoys.cleanup();
                sender.sendMessage(TextFormat.GREEN + "Envoy chests cleared.");
            }
            default -> sender.sendMessage(TextFormat.YELLOW + "/envoy start | /envoy setcenter <radius> | /envoy clear");
        }
        return true;
    }

    private void setCenter(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "Stand at the desired center and run this in-game.");
            return;
        }
        double radius = 50;
        if (args.length >= 2) {
            try {
                radius = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(TextFormat.RED + "Invalid radius.");
                return;
            }
        }
        envoys.setCenter(player.getLevel(), player.x, player.y, player.z, radius);
        sender.sendMessage(TextFormat.GREEN + "Envoy center set here with radius " + radius + ".");
    }
}

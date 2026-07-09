package net.factionscore.faction;

import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.utils.TextFormat;

public final class FactionsAdminCommand extends Command {

    private final FactionManager factions;

    public FactionsAdminCommand(FactionManager factions) {
        super("fadmin", "Factions admin command", "/fadmin help");
        this.factions = factions;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(TextFormat.YELLOW + "/fadmin setpower <faction> <power> | safezone <faction> | warzone <faction> | delete <faction>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "setpower" -> setPower(sender, args);
            case "safezone" -> toggle(sender, args, true, false);
            case "warzone" -> toggle(sender, args, false, true);
            case "delete" -> delete(sender, args);
            default -> sender.sendMessage(TextFormat.RED + "Unknown subcommand.");
        }
        return true;
    }

    private void setPower(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextFormat.RED + "Usage: /fadmin setpower <faction> <power>");
            return;
        }
        factions.getByName(args[1]).ifPresentOrElse(faction -> {
            try {
                faction.setPower(Double.parseDouble(args[2]));
                sender.sendMessage(TextFormat.GREEN + "Set " + faction.getName() + " power to " + faction.getPower());
            } catch (NumberFormatException e) {
                sender.sendMessage(TextFormat.RED + "Invalid number.");
            }
        }, () -> sender.sendMessage(TextFormat.RED + "No such faction."));
    }

    private void toggle(CommandSender sender, String[] args, boolean safezone, boolean warzone) {
        if (args.length < 2) {
            sender.sendMessage(TextFormat.RED + "Usage: /fadmin " + args[0] + " <faction>");
            return;
        }
        factions.getByName(args[1]).ifPresentOrElse(faction -> {
            if (safezone) faction.setSafezone(!faction.isSafezone());
            if (warzone) faction.setWarzone(!faction.isWarzone());
            sender.sendMessage(TextFormat.GREEN + faction.getName() + " safezone=" + faction.isSafezone() + " warzone=" + faction.isWarzone());
        }, () -> sender.sendMessage(TextFormat.RED + "No such faction."));
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextFormat.RED + "Usage: /fadmin delete <faction>");
            return;
        }
        factions.getByName(args[1]).ifPresentOrElse(faction -> {
            factions.disband(faction);
            sender.sendMessage(TextFormat.GREEN + "Deleted " + args[1] + ".");
        }, () -> sender.sendMessage(TextFormat.RED + "No such faction."));
    }
}

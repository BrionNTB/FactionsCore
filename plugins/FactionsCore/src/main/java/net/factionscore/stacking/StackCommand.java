package net.factionscore.stacking;

import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.utils.TextFormat;

public final class StackCommand extends Command {

    public StackCommand() {
        super("stack", "Mob/spawner stacking controls", "/stack help");
        this.setPermission("factionscore.command.stack");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        sender.sendMessage(TextFormat.GOLD + "--- Stacking ---");
        sender.sendMessage(TextFormat.YELLOW + "Mobs of the same type merge automatically within range.");
        sender.sendMessage(TextFormat.YELLOW + "Sneak + right-click a spawner to increase its stack size.");
        return true;
    }
}

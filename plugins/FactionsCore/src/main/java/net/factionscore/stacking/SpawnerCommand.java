package net.factionscore.stacking;

import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.item.Item;
import org.powernukkitx.registry.Registries;
import org.powernukkitx.utils.TextFormat;

/**
 * Gives mob spawner items pre-typed to a specific mob (a vanilla spawner item carrying the mob in
 * NBT; {@link SpawnerItemListener} types the block entity on placement). This is how factions
 * servers sell/distribute "Zombie Spawner", "Blaze Spawner" etc. -- also usable from crate loot
 * via command entries like {@code spawner {player} zombie}.
 */
public final class SpawnerCommand extends Command {

    public static final String MOB_TAG = "FCSpawnMob";

    public SpawnerCommand() {
        super("spawner", "Give a pre-typed mob spawner", "/spawner <player> <mob> [count]");
        this.setPermission("factionscore.command.spawner");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextFormat.RED + "Usage: /spawner <player> <mob> [count]  (e.g. /spawner Steve zombie)");
            return true;
        }
        Player target = Server.getInstance().getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(TextFormat.RED + "Player not found.");
            return true;
        }
        String mobId = args[1].toLowerCase();
        if (!mobId.contains(":")) {
            mobId = "minecraft:" + mobId;
        }
        int networkId = Registries.ENTITY.getEntityNetworkId(mobId);
        if (networkId <= 0) {
            sender.sendMessage(TextFormat.RED + "Unknown mob: " + args[1]);
            return true;
        }
        int count = 1;
        if (args.length >= 3) {
            try {
                count = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                sender.sendMessage(TextFormat.RED + "Invalid count.");
                return true;
            }
        }

        String mobName = prettyName(mobId);
        Item spawner = Item.get(BlockID.MOB_SPAWNER, 0, count);
        spawner.getOrCreateNbt().putString(MOB_TAG, mobId);
        spawner.setNbt(spawner.getNbt());
        spawner.setCustomName(TextFormat.RESET + "" + TextFormat.BOLD + "" + TextFormat.AQUA + mobName + " Spawner");
        spawner.setLore(
                TextFormat.GRAY + "Spawns " + TextFormat.AQUA + mobName + TextFormat.GRAY + " -- no dungeon hunting needed.",
                TextFormat.DARK_GRAY + "" + TextFormat.ITALIC + "Sneak + right-click placed spawners to stack them.");

        for (Item leftover : target.getInventory().addItem(spawner)) {
            target.getLevel().dropItem(target, leftover);
        }
        sender.sendMessage(TextFormat.GREEN + "Gave " + target.getName() + " " + count + "x " + mobName + " Spawner.");
        return true;
    }

    private String prettyName(String mobId) {
        String path = mobId.substring(mobId.indexOf(':') + 1).replace('_', ' ');
        StringBuilder pretty = new StringBuilder();
        for (String word : path.split(" ")) {
            if (word.isEmpty()) continue;
            pretty.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return pretty.toString().trim();
    }
}

package net.factionscore.schematic;

import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.level.Level;
import org.powernukkitx.math.BlockFace;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.utils.TextFormat;

public final class SchemCommand extends Command {

    private final SchematicManager schematics;

    public SchemCommand(SchematicManager schematics) {
        super("schem", "Paste saved structures (cannons etc.)", "/schem <list|paste <name>|reload>");
        this.setPermission("factionscore.command.schem");
        this.schematics = schematics;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "list";
        switch (sub) {
            case "list" -> {
                if (schematics.all().isEmpty()) {
                    sender.sendMessage(TextFormat.RED + "No schematics loaded. Drop .mcstructure files into plugins/FactionsCore/schematics/");
                    return true;
                }
                sender.sendMessage(TextFormat.GOLD + "" + TextFormat.BOLD + "Schematics:");
                schematics.all().forEach((name, s) -> sender.sendMessage(TextFormat.YELLOW + " " + name
                        + TextFormat.GRAY + " (" + s.sizeX() + "x" + s.sizeY() + "x" + s.sizeZ() + ")"));
                sender.sendMessage(TextFormat.GRAY + "Paste with /schem paste <name>");
            }
            case "reload" -> {
                schematics.reload();
                sender.sendMessage(TextFormat.GREEN + "Reloaded " + schematics.all().size() + " schematic(s).");
            }
            case "paste" -> {
                if (args.length < 2) {
                    sender.sendMessage(TextFormat.RED + "Usage: /schem paste <name> [material]");
                    return true;
                }
                McStructure structure = schematics.get(args[1]);
                if (structure == null) {
                    sender.sendMessage(TextFormat.RED + "Unknown schematic. See /schem list");
                    return true;
                }
                org.powernukkitx.block.Block frameMaterial = null;
                boolean fillTnt = true;
                for (int i = 2; i < args.length; i++) {
                    if (args[i].equalsIgnoreCase("notnt")) {
                        fillTnt = false;
                        continue;
                    }
                    String id = args[i].contains(":") ? args[i] : "minecraft:" + args[i].toLowerCase();
                    frameMaterial = org.powernukkitx.registry.Registries.BLOCK.get(id);
                    if (frameMaterial == null) {
                        sender.sendMessage(TextFormat.RED + "Unknown block '" + args[i] + "' -- try cobblestone, obsidian, bedrock, or 'notnt'.");
                        return true;
                    }
                }
                Level level;
                Vector3 corner;
                if (sender instanceof Player player) {
                    level = player.getLevel();
                    // Drop the min corner a couple of blocks ahead so the player isn't entombed.
                    BlockFace facing = player.getHorizontalFacing();
                    corner = new Vector3(player.getFloorX(), player.getFloorY(), player.getFloorZ())
                            .add(facing.getXOffset() * 2, 0, facing.getZOffset() * 2);
                    // If pasting toward negative X/Z, shift the corner so the structure still
                    // lands in front of the player instead of on top of them.
                    if (facing.getXOffset() < 0) corner = corner.add(-(structure.sizeX() - 1), 0, 0);
                    if (facing.getZOffset() < 0) corner = corner.add(0, 0, -(structure.sizeZ() - 1));
                } else {
                    level = Server.getInstance().getDefaultLevel();
                    corner = level.getSafeSpawn().add(2, 0, 2);
                }
                int placed = schematics.paste(structure, level, corner, fillTnt, frameMaterial);
                sender.sendMessage(TextFormat.GREEN + "Pasted " + args[1]
                        + (frameMaterial != null ? " in " + frameMaterial.getId().replace("minecraft:", "") : "")
                        + TextFormat.GRAY + " (" + placed
                        + " blocks, corner " + corner.getFloorX() + "," + corner.getFloorY() + "," + corner.getFloorZ()
                        + ", laid out toward east/south as saved)." + TextFormat.GREEN
                        + (fillTnt ? " Dispensers come pre-loaded with TNT." : " Dispensers left EMPTY (notnt)."));
                if (!structure.unresolvedBlocks().isEmpty()) {
                    sender.sendMessage(TextFormat.RED + "Some blocks couldn't be mapped and were skipped: "
                            + structure.unresolvedBlocks());
                }
            }
            default -> sender.sendMessage(TextFormat.RED + "Usage: /schem <list|paste <name>|reload>");
        }
        return true;
    }
}

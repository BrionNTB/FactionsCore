package net.factionscore.faction;

import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.level.Position;
import org.powernukkitx.utils.TextFormat;

import java.util.Comparator;
import java.util.UUID;

public final class FactionsCommand extends Command {

    private final FactionManager factions;
    private final FactionValueManager values;
    private final net.factionscore.faction.listener.FactionChatListener chat;

    public FactionsCommand(FactionManager factions, FactionValueManager values, net.factionscore.faction.listener.FactionChatListener chat) {
        super("f", "Factions command", "/f help", new String[]{"factions", "faction"});
        this.setPermission("factionscore.command.f");
        this.factions = factions;
        this.values = values;
        this.chat = chat;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            args = new String[]{"help"};
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "create" -> create(sender, args);
            case "disband" -> disband(sender);
            case "invite" -> invite(sender, args);
            case "join" -> join(sender, args);
            case "leave" -> leave(sender);
            case "kick" -> kick(sender, args);
            case "claim" -> claim(sender);
            case "unclaim" -> unclaim(sender);
            case "sethome" -> setHome(sender);
            case "home" -> home(sender);
            case "ally", "enemy", "truce", "neutral" -> relate(sender, args, sub);
            case "power" -> power(sender);
            case "info" -> info(sender, args);
            case "list" -> list(sender);
            case "value" -> value(sender, args);
            case "top" -> top(sender);
            case "chat", "c" -> toggleChat(sender);
            case "map" -> map(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(TextFormat.GOLD + "--- FactionsCore ---");
        for (String line : new String[]{
                "/f create <name>", "/f disband", "/f invite <player>", "/f join <faction>",
                "/f leave", "/f kick <player>", "/f claim", "/f unclaim", "/f sethome", "/f home",
                "/f ally|enemy|truce|neutral <faction>", "/f power", "/f info [faction]", "/f list",
                "/f value [faction]", "/f top", "/f chat", "/f map"
        }) {
            sender.sendMessage(TextFormat.YELLOW + line);
        }
    }

    private Player asPlayer(CommandSender sender) {
        return sender instanceof Player player ? player : null;
    }

    /**
     * Chat chunk map centered on the player, so claim edges are visible in-game: green = your
     * faction's claims, red = another faction, gray = wilderness, white + = the chunk you stand in.
     */
    private void map(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            sender.sendMessage(TextFormat.RED + "Only players can use /f map.");
            return;
        }
        String levelName = player.getLevel().getName();
        int centerX = player.getChunkX();
        int centerZ = player.getChunkZ();
        Faction own = factions.getByMember(player.getUniqueId()).orElse(null);

        sender.sendMessage(TextFormat.GOLD + "" + TextFormat.BOLD + "--- Claim Map " + TextFormat.RESET
                + TextFormat.GRAY + "(chunk " + centerX + ", " + centerZ + ") " + TextFormat.GOLD + TextFormat.BOLD + "---");
        int radius = 4;
        for (int dz = -radius; dz <= radius; dz++) {
            StringBuilder row = new StringBuilder();
            for (int dx = -radius; dx <= radius; dx++) {
                Faction owner = factions.getClaimOwner(levelName, centerX + dx, centerZ + dz).orElse(null);
                boolean here = dx == 0 && dz == 0;
                String symbol = here ? "+" : "■";
                if (owner == null) {
                    row.append(here ? TextFormat.WHITE + "" + TextFormat.BOLD : TextFormat.DARK_GRAY.toString()).append(symbol);
                } else if (own != null && owner.getId().equals(own.getId())) {
                    row.append(here ? TextFormat.GREEN + "" + TextFormat.BOLD : TextFormat.GREEN.toString()).append(symbol);
                } else {
                    row.append(here ? TextFormat.RED + "" + TextFormat.BOLD : TextFormat.RED.toString()).append(symbol);
                }
                row.append(TextFormat.RESET).append(" ");
            }
            sender.sendMessage(row.toString());
        }
        sender.sendMessage(TextFormat.GREEN + "■ yours  " + TextFormat.RED + "■ enemy/other  "
                + TextFormat.DARK_GRAY + "■ wilderness  " + TextFormat.WHITE + "+ you");
    }

    private void create(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            sender.sendMessage(TextFormat.RED + "Only players can create factions.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(TextFormat.RED + "Usage: /f create <name>");
            return;
        }
        try {
            Faction faction = factions.create(args[1], player.getUniqueId());
            sender.sendMessage(TextFormat.GREEN + "Faction " + faction.getName() + " created.");
        } catch (FactionManager.FactionException e) {
            sender.sendMessage(TextFormat.RED + e.getMessage());
        }
    }

    private void disband(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) return;
        Faction faction = requireFaction(sender, player);
        if (faction == null) return;
        if (!faction.roleOf(player.getUniqueId()).canDisband()) {
            sender.sendMessage(TextFormat.RED + "Only the leader can disband the faction.");
            return;
        }
        factions.disband(faction);
        sender.sendMessage(TextFormat.YELLOW + "Faction disbanded.");
    }

    private void invite(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) return;
        Faction faction = requireFaction(sender, player);
        if (faction == null) return;
        if (!faction.roleOf(player.getUniqueId()).canInvite()) {
            sender.sendMessage(TextFormat.RED + "You cannot invite members.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(TextFormat.RED + "Usage: /f invite <player>");
            return;
        }
        Player target = Server.getInstance().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(TextFormat.RED + "Player not found.");
            return;
        }
        target.sendMessage(TextFormat.GOLD + player.getName() + " invited you to join " + faction.getName() + ". Use /f join " + faction.getName());
        sender.sendMessage(TextFormat.GREEN + "Invited " + target.getName() + ".");
    }

    private void join(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) return;
        if (factions.getByMember(player.getUniqueId()).isPresent()) {
            sender.sendMessage(TextFormat.RED + "You are already in a faction.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(TextFormat.RED + "Usage: /f join <faction>");
            return;
        }
        factions.getByName(args[1]).ifPresentOrElse(faction -> {
            factions.join(faction, player.getUniqueId(), FactionRole.RECRUIT);
            sender.sendMessage(TextFormat.GREEN + "Joined " + faction.getName() + ".");
        }, () -> sender.sendMessage(TextFormat.RED + "No such faction."));
    }

    private void leave(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) return;
        Faction faction = requireFaction(sender, player);
        if (faction == null) return;
        factions.leave(faction, player.getUniqueId());
        sender.sendMessage(TextFormat.YELLOW + "You left " + faction.getName() + ".");
    }

    private void kick(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) return;
        Faction faction = requireFaction(sender, player);
        if (faction == null) return;
        if (!faction.roleOf(player.getUniqueId()).canKick()) {
            sender.sendMessage(TextFormat.RED + "You cannot kick members.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(TextFormat.RED + "Usage: /f kick <player>");
            return;
        }
        Player target = Server.getInstance().getPlayerExact(args[1]);
        UUID targetId = target != null ? target.getUniqueId() : null;
        if (targetId == null || !faction.isMember(targetId)) {
            sender.sendMessage(TextFormat.RED + "That player is not in your faction.");
            return;
        }
        factions.leave(faction, targetId);
        sender.sendMessage(TextFormat.GREEN + "Kicked " + args[1] + ".");
    }

    private void claim(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) return;
        Faction faction = requireFaction(sender, player);
        if (faction == null) return;
        if (!faction.roleOf(player.getUniqueId()).canManageClaims()) {
            sender.sendMessage(TextFormat.RED + "You cannot manage claims.");
            return;
        }
        String levelName = player.getLevel().getName();
        int cx = player.getFloorX() >> 4;
        int cz = player.getFloorZ() >> 4;
        var existing = factions.getClaimOwner(levelName, cx, cz);
        if (existing.isPresent()) {
            sender.sendMessage(TextFormat.RED + "This chunk is already claimed by " + existing.get().getName() + ".");
            return;
        }
        if (factions.claim(faction, levelName, cx, cz)) {
            sender.sendMessage(TextFormat.GREEN + "Chunk claimed for " + faction.getName() + ".");
        } else {
            sender.sendMessage(TextFormat.RED + "Not enough power to claim more land.");
        }
    }

    private void unclaim(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) return;
        Faction faction = requireFaction(sender, player);
        if (faction == null) return;
        if (!faction.roleOf(player.getUniqueId()).canManageClaims()) {
            sender.sendMessage(TextFormat.RED + "You cannot manage claims.");
            return;
        }
        String levelName = player.getLevel().getName();
        int cx = player.getFloorX() >> 4;
        int cz = player.getFloorZ() >> 4;
        if (factions.unclaim(faction, levelName, cx, cz)) {
            sender.sendMessage(TextFormat.GREEN + "Chunk unclaimed.");
        } else {
            sender.sendMessage(TextFormat.RED + "This chunk isn't claimed by your faction.");
        }
    }

    private void setHome(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) return;
        Faction faction = requireFaction(sender, player);
        if (faction == null) return;
        factions.setHome(faction, new Position(player.x, player.y, player.z, player.getLevel()));
        sender.sendMessage(TextFormat.GREEN + "Faction home set.");
    }

    private void home(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) return;
        Faction faction = requireFaction(sender, player);
        if (faction == null) return;
        Position home = faction.getHome();
        if (home == null) {
            sender.sendMessage(TextFormat.RED + "Your faction has no home set.");
            return;
        }
        player.teleport(home);
        sender.sendMessage(TextFormat.GREEN + "Teleported home.");
    }

    private void relate(CommandSender sender, String[] args, String sub) {
        Player player = asPlayer(sender);
        if (player == null) return;
        Faction faction = requireFaction(sender, player);
        if (faction == null) return;
        if (!faction.roleOf(player.getUniqueId()).canSetRelations()) {
            sender.sendMessage(TextFormat.RED + "Only the leader can set relations.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(TextFormat.RED + "Usage: /f " + sub + " <faction>");
            return;
        }
        var other = factions.getByName(args[1]);
        if (other.isEmpty() || other.get() == faction) {
            sender.sendMessage(TextFormat.RED + "No such faction.");
            return;
        }
        RelationType type = RelationType.valueOf(sub.toUpperCase());
        factions.setRelation(faction, other.get(), type);
        sender.sendMessage(TextFormat.GREEN + "Relation with " + other.get().getName() + " set to " + type + ".");
    }

    private void power(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) return;
        Faction faction = requireFaction(sender, player);
        if (faction == null) return;
        sender.sendMessage(TextFormat.GOLD + faction.getName() + " power: " + faction.getPower());
    }

    private void info(CommandSender sender, String[] args) {
        Faction faction;
        if (args.length >= 2) {
            faction = factions.getByName(args[1]).orElse(null);
        } else {
            Player player = asPlayer(sender);
            faction = player == null ? null : factions.getByMember(player.getUniqueId()).orElse(null);
        }
        if (faction == null) {
            sender.sendMessage(TextFormat.RED + "No such faction.");
            return;
        }
        sender.sendMessage(TextFormat.GOLD + "=== " + faction.getName() + " ===");
        sender.sendMessage(TextFormat.YELLOW + "Power: " + faction.getPower());
        sender.sendMessage(TextFormat.YELLOW + "Members: " + faction.members().size());
        sender.sendMessage(TextFormat.YELLOW + "Value: " + (long) values.totalValue(faction)
                + TextFormat.GRAY + " (spawners: " + (long) faction.getSpawnerValue() + ")");
    }

    private void value(CommandSender sender, String[] args) {
        Faction faction;
        if (args.length >= 2) {
            faction = factions.getByName(args[1]).orElse(null);
        } else {
            Player player = asPlayer(sender);
            faction = player == null ? null : factions.getByMember(player.getUniqueId()).orElse(null);
        }
        if (faction == null) {
            sender.sendMessage(TextFormat.RED + "No such faction.");
            return;
        }
        sender.sendMessage(TextFormat.GOLD + faction.getName() + " value: " + (long) values.totalValue(faction)
                + TextFormat.GRAY + " (spawners contribute: " + (long) faction.getSpawnerValue() + ")");
    }

    private void top(CommandSender sender) {
        sender.sendMessage(TextFormat.GOLD + "--- Faction value leaderboard ---");
        factions.all().stream()
                .sorted(Comparator.comparingDouble(values::totalValue).reversed())
                .limit(10)
                .forEach(faction -> sender.sendMessage(TextFormat.YELLOW + faction.getName()
                        + TextFormat.GRAY + " - " + (long) values.totalValue(faction)));
    }

    private void list(CommandSender sender) {
        sender.sendMessage(TextFormat.GOLD + "--- Factions (" + factions.all().size() + ") ---");
        for (Faction faction : factions.all()) {
            sender.sendMessage(TextFormat.YELLOW + faction.getName() + TextFormat.GRAY + " (" + faction.members().size() + " members, " + faction.getPower() + " power)");
        }
    }

    private void toggleChat(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) return;
        if (requireFaction(sender, player) == null) return;
        boolean enabled = chat.toggle(player.getUniqueId());
        sender.sendMessage(enabled
                ? TextFormat.GREEN + "Faction chat ON -- only your faction sees your messages. /f chat to switch back."
                : TextFormat.YELLOW + "Faction chat OFF -- back to public chat.");
    }

    private Faction requireFaction(CommandSender sender, Player player) {
        var faction = factions.getByMember(player.getUniqueId());
        if (faction.isEmpty()) {
            sender.sendMessage(TextFormat.RED + "You are not in a faction.");
            return null;
        }
        return faction.get();
    }
}

package net.factionscore.misc;

import net.factionscore.border.WorldBorderManager;
import net.factionscore.faction.FactionManager;
import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.Position;
import org.powernukkitx.utils.Config;
import org.powernukkitx.utils.TextFormat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random teleport into the wilderness -- the standard way players spread out to build bases on a
 * factions server. Picks random coordinates inside the world border, skips liquids and claimed
 * chunks, and lands the player on the surface.
 */
public final class WildCommand extends Command {

    private final FactionManager factions;
    private final WorldBorderManager border;
    private final WarmupManager warmups;
    private final Config config;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public WildCommand(FactionManager factions, WorldBorderManager border, WarmupManager warmups, Config config) {
        super("wild", "Teleport to a random wilderness location", "/wild", new String[]{"rtp"});
        this.setPermission("factionscore.command.wild");
        this.factions = factions;
        this.border = border;
        this.warmups = warmups;
        this.config = config;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "Only players can use /wild.");
            return true;
        }

        long cooldownMs = config.getInt("wild.cooldown-seconds", 60) * 1000L;
        Long last = cooldowns.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < cooldownMs) {
            player.sendMessage(TextFormat.RED + "You can use /wild again in " + ((cooldownMs - (now - last)) / 1000 + 1) + "s.");
            return true;
        }

        warmups.start(player, "Wild", () -> {
            if (!player.isOnline()) return;
            randomTeleport(player);
        });
        return true;
    }

    private void randomTeleport(Player player) {
        Level level = player.getLevel();
        double maxRadius = Math.min(config.getDouble("wild.max-radius", 10000), border.radiusFor(level) - 16);
        double minRadius = config.getDouble("wild.min-radius", 500);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = minRadius + random.nextDouble() * (maxRadius - minRadius);
            int x = (int) Math.round(Math.cos(angle) * distance);
            int z = (int) Math.round(Math.sin(angle) * distance);

            if (factions.getClaimOwner(level.getName(), x >> 4, z >> 4).isPresent()) {
                continue;
            }
            // Only consider already-generated terrain: getHighestBlockAt on an ungenerated chunk
            // would force synchronous generation on the main thread.
            if (!level.isChunkGenerated(x >> 4, z >> 4)) {
                continue;
            }
            int y = level.getHighestBlockAt(x, z);
            var ground = level.getBlock(x, y, z);
            if (ground.isAir() || isLiquid(ground.getId())) {
                continue;
            }

            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            player.teleport(new Position(x + 0.5, y + 1.5, z + 0.5, level));
            player.sendMessage(TextFormat.GREEN + "Whoosh! You landed at " + x + ", " + (y + 1) + ", " + z + ".");
            return;
        }

        player.sendMessage(TextFormat.RED + "Couldn't find a safe spot -- try again (nearby terrain may still be generating).");
    }

    private boolean isLiquid(String id) {
        return id.contains("water") || id.contains("lava");
    }
}

package net.factionscore.misc;

import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.block.Block;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.entity.Entity;
import org.powernukkitx.entity.EntityID;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.EventPriority;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.entity.EntityExplodeEvent;
import org.powernukkitx.level.GameRule;
import org.powernukkitx.level.Level;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.utils.TextFormat;

/**
 * Admin diagnostic for "TNT explodes but doesn't break blocks" reports. Spawns a primed TNT at
 * the sender's feet (or at world spawn from console), then reports the game rules, whether the
 * spot counts as underwater (vanilla: explosions in water never break blocks), and how many
 * blocks the resulting explosion actually destroyed -- enough to tell a misconfigured game rule
 * from a water spot from a real engine bug, straight from in-game chat.
 */
public final class TntTestCommand extends Command implements Listener {

    private final org.powernukkitx.plugin.Plugin plugin;
    private volatile CommandSender pending;

    public TntTestCommand(org.powernukkitx.plugin.Plugin plugin) {
        super("tnttest", "Diagnose TNT block damage at your position", "/tnttest [cannon]");
        this.setPermission("factionscore.command.fadmin");
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Level level;
        Vector3 pos;
        if (sender instanceof Player player) {
            level = player.getLevel();
            pos = new Vector3(player.getFloorX() + 0.5, player.getFloorY(), player.getFloorZ() + 0.5);
        } else {
            level = Server.getInstance().getDefaultLevel();
            pos = level.getSafeSpawn().add(0.5, 0, 0.5);
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("cannon")) {
            return cannonTest(sender, level, pos);
        }

        sender.sendMessage(TextFormat.YELLOW + "[tnttest] gamerule tntExplodes = "
                + level.getGameRules().getBoolean(GameRule.TNT_EXPLODES)
                + ", mobGriefing (creepers) = " + level.getGameRules().getBoolean(GameRule.MOB_GRIEFING));

        Block layer0 = level.getBlock(pos.floor(), 0);
        Block layer1 = level.getBlock(pos.floor(), 1);
        boolean watery = layer0.getId().contains("water") || layer1.getId().contains("water");
        sender.sendMessage(TextFormat.YELLOW + "[tnttest] block here: " + layer0.getId()
                + (watery ? TextFormat.RED + " -- UNDERWATER: explosions here never break blocks (vanilla rule)" : ""));

        CompoundTag nbt = Entity.getDefaultNBT(pos).putByte("Fuse", 40);
        Entity tnt = Entity.createEntity(EntityID.TNT, level.getChunk(pos.getChunkX(), pos.getChunkZ(), true), nbt);
        if (tnt == null) {
            sender.sendMessage(TextFormat.RED + "[tnttest] could not spawn the TNT entity!");
            return true;
        }
        pending = sender;
        tnt.spawnToAll();
        sender.sendMessage(TextFormat.YELLOW + "[tnttest] TNT spawned at your feet -- step back! Exploding in 2 seconds...");
        return true;
    }

    /**
     * Cannon physics probe: detonates one TNT next to a longer-fused one and reports how far the
     * blast launched it. Java 1.8.8 cannons are built entirely on this propulsion, so "launched
     * 0.0 blocks" means cannons cannot work and the explosion knockback path is broken.
     */
    private boolean cannonTest(CommandSender sender, Level level, Vector3 pos) {
        if (!(sender instanceof Player)) {
            // Console runs get a blast-proof platform high in the sky: world spawn may be ocean,
            // and TNT sinking through water would measure water drag instead of knockback.
            pos = new Vector3(pos.getFloorX() + 0.5, 150, pos.getFloorZ() + 0.5);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    level.setBlock(new Vector3(pos.getFloorX() + dx, 149, pos.getFloorZ() + dz),
                            org.powernukkitx.block.Block.get(org.powernukkitx.block.BlockID.OBSIDIAN));
                }
            }
        }
        CompoundTag chargeNbt = Entity.getDefaultNBT(pos.add(0, 1, 0)).putByte("Fuse", 20);
        Entity charge = Entity.createEntity(EntityID.TNT, level.getChunk(pos.getChunkX(), pos.getChunkZ(), true), chargeNbt);
        Vector3 projectileStart = pos.add(1.2, 1, 0);
        CompoundTag projectileNbt = Entity.getDefaultNBT(projectileStart).putByte("Fuse", 120);
        Entity projectile = Entity.createEntity(EntityID.TNT, level.getChunk(pos.getChunkX(), pos.getChunkZ(), true), projectileNbt);
        if (charge == null || projectile == null) {
            sender.sendMessage(TextFormat.RED + "[tnttest] could not spawn the TNT entities!");
            return true;
        }
        charge.spawnToAll();
        projectile.spawnToAll();
        sender.sendMessage(TextFormat.YELLOW + "[tnttest] cannon probe armed -- step back! Measuring in 4 seconds...");
        Server.getInstance().getScheduler().scheduleDelayedTask(plugin, () -> {
            if (projectile.isClosed()) {
                sender.sendMessage(TextFormat.RED + "[tnttest] projectile TNT vanished before it could be measured.");
                return;
            }
            double travelled = Math.sqrt(Math.pow(projectile.x - projectileStart.x, 2) + Math.pow(projectile.z - projectileStart.z, 2));
            if (travelled < 0.5) {
                sender.sendMessage(TextFormat.RED + String.format("[tnttest] projectile only moved %.2f blocks -- explosion knockback is broken.", travelled));
            } else {
                sender.sendMessage(TextFormat.GREEN + String.format("[tnttest] blast launched the projectile %.1f blocks -- cannon physics working.", travelled));
            }
        }, 80);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExplode(EntityExplodeEvent event) {
        CommandSender sender = pending;
        if (sender == null) return;
        pending = null;
        if (event.isCancelled()) {
            sender.sendMessage(TextFormat.RED + "[tnttest] explosion was CANCELLED by a plugin -- that's the bug.");
            return;
        }
        int count = event.getBlockList().size();
        if (count == 0) {
            sender.sendMessage(TextFormat.RED + "[tnttest] explosion happened but hit 0 blocks. If you weren't in "
                    + "water or mid-air, report this.");
        } else {
            sender.sendMessage(TextFormat.GREEN + "[tnttest] explosion destroyed " + count
                    + " blocks -- TNT block damage is working here.");
        }
    }
}

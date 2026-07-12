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

    private volatile CommandSender pending;

    public TntTestCommand() {
        super("tnttest", "Diagnose TNT block damage at your position", "/tnttest");
        this.setPermission("factionscore.command.fadmin");
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

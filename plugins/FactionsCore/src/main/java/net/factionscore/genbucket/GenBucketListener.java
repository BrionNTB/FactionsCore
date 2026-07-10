package net.factionscore.genbucket;

import net.factionscore.faction.FactionManager;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.block.Block;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.EventPriority;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.player.PlayerInteractEvent;
import org.powernukkitx.item.Item;
import org.powernukkitx.level.Level;
import org.powernukkitx.math.BlockFace;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.plugin.Plugin;
import org.powernukkitx.utils.Config;
import org.powernukkitx.utils.TextFormat;

public final class GenBucketListener implements Listener {

    private final FactionManager factions;
    private final Config config;
    private final Plugin plugin;

    public GenBucketListener(FactionManager factions, Config config, Plugin plugin) {
        this.factions = factions;
        this.config = config;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!(player.getInventory().getItemInMainHand() instanceof GenBucketItem bucket)) return;
        Block clicked = event.getBlock();
        BlockFace face = event.getFace();
        if (clicked == null || face == null) return;

        event.setCancelled(true);

        Level level = clicked.getLevel();
        if (!canBuildHere(player, level, clicked)) {
            player.sendMessage(TextFormat.RED + "You can't gen in another faction's territory.");
            return;
        }

        // Vertical faces generate a column upward (wall-building); side faces generate a
        // horizontal run extending away from the face you clicked.
        BlockFace direction = (face == BlockFace.UP || face == BlockFace.DOWN) ? BlockFace.UP : face;
        Vector3 start = clicked.getSide(face).asBlockVector3().asVector3();

        Item held = player.getInventory().getItemInMainHand();
        Item remaining = held.clone();
        remaining.setCount(remaining.getCount() - 1);
        player.getInventory().setItemInMainHand(remaining.getCount() <= 0 ? Item.get(BlockID.AIR) : remaining);

        int maxLength = config.getInt("genbuckets.max-length", 40);
        int delayTicks = Math.max(1, config.getInt("genbuckets.ticks-per-block", 4));
        placeNext(player, level, start, direction, bucket.getGeneratesBlockId(), maxLength, delayTicks);
        player.sendMessage(TextFormat.GREEN + "Gen started.");
    }

    private boolean canBuildHere(Player player, Level level, Vector3 pos) {
        var owner = factions.getClaimOwner(level.getName(), pos.getFloorX() >> 4, pos.getFloorZ() >> 4);
        if (owner.isEmpty()) return true;
        return owner.get().isMember(player.getUniqueId()) || player.hasPermission("factionscore.bypass.claims");
    }

    /**
     * Places one block then schedules the next -- self-chaining so a cancelled/finished gen just
     * stops scheduling instead of needing task-handle bookkeeping. Stops at the first non-air
     * block, world limits, claim boundaries, or max length.
     */
    private void placeNext(Player player, Level level, Vector3 pos, BlockFace direction, String blockId, int remaining, int delayTicks) {
        if (remaining <= 0) return;
        if (!level.isYInRange(pos.getFloorY())) return;
        if (!level.getBlock(pos).isAir()) return;
        if (!canBuildHere(player, level, pos)) return;

        level.setBlock(pos, Block.get(blockId));
        Vector3 next = pos.getSide(direction);
        Server.getInstance().getScheduler().scheduleDelayedTask(plugin,
                () -> placeNext(player, level, next, direction, blockId, remaining - 1, delayTicks), delayTicks);
    }
}

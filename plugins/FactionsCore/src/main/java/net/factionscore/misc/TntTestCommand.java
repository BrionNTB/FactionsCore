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
        if (args.length > 0 && args[0].equalsIgnoreCase("stream")) {
            return streamTest(sender, level, pos);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("arm")) {
            int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 30;
            armedUntil = System.currentTimeMillis() + seconds * 1000L;
            pending = sender;
            // Dummy chunk loader so redstone/liquids around here tick even with no player nearby
            // (scheduled block updates only run in chunks near a loader) -- lets the console fire
            // pasted cannons. Unregisters itself when the window closes.
            final Vector3 armPos = pos;
            final Level armLevel = level;
            org.powernukkitx.level.ChunkLoader loader = new org.powernukkitx.level.ChunkLoader() {
                public int getLoaderId() {
                    return Integer.MAX_VALUE - 17;
                }
                public boolean isLoaderActive() {
                    return true;
                }
                public org.powernukkitx.level.Position getPosition() {
                    return org.powernukkitx.level.Position.fromObject(armPos, armLevel);
                }
                public double getX() {
                    return armPos.x;
                }
                public double getZ() {
                    return armPos.z;
                }
                public Level getLevel() {
                    return armLevel;
                }
                public void onChunkChanged(org.powernukkitx.level.format.IChunk chunk) {
                }
                public void onChunkLoaded(org.powernukkitx.level.format.IChunk chunk) {
                }
                public void onChunkUnloaded(org.powernukkitx.level.format.IChunk chunk) {
                }
            };
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.registerChunkLoader(loader, (pos.getFloorX() >> 4) + dx, (pos.getFloorZ() >> 4) + dz, true);
                }
            }
            Server.getInstance().getScheduler().scheduleDelayedTask(plugin, () -> {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        armLevel.unregisterChunkLoader(loader, (armPos.getFloorX() >> 4) + dx, (armPos.getFloorZ() >> 4) + dz);
                    }
                }
            }, seconds * 20);
            sender.sendMessage(TextFormat.YELLOW + "[tnttest] armed for " + seconds + "s: explosions will be reported, chunks here are ticking.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("shot")) {
            return shotTest(sender, level, pos, args.length > 1 ? Integer.parseInt(args[1]) : 1);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("box")) {
            return boxTest(sender, level, pos,
                    args.length > 1 ? Integer.parseInt(args[1]) : 6,
                    args.length > 2 ? Integer.parseInt(args[2]) : 2);
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
        armedUntil = System.currentTimeMillis() + 8000;
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

    /**
     * Water-carry probe: builds an enclosed obsidian channel in the sky, pours a water source in
     * at one end, drops a long-fused TNT into the stream, and reports how far the current carried
     * it. Cannon barrels feed their charge down exactly this kind of stream, so "drifted 0.0
     * blocks" means water currents aren't moving primed TNT and cannons cannot feed.
     */
    private boolean streamTest(CommandSender sender, Level level, Vector3 posIn) {
        final Vector3 base = sender instanceof Player
                ? new Vector3(posIn.getFloorX(), 150, posIn.getFloorZ())
                : new Vector3(posIn.getFloorX(), 150, posIn.getFloorZ());
        org.powernukkitx.block.Block obsidian = org.powernukkitx.block.Block.get(org.powernukkitx.block.BlockID.OBSIDIAN);
        for (int dx = -1; dx <= 9; dx++) {
            level.setBlock(new Vector3(base.x + dx, 149, base.z), obsidian);          // floor
            level.setBlock(new Vector3(base.x + dx, 150, base.z - 1), obsidian);      // wall
            level.setBlock(new Vector3(base.x + dx, 150, base.z + 1), obsidian);      // wall
            if (dx == -1 || dx == 9) {
                level.setBlock(new Vector3(base.x + dx, 150, base.z), obsidian);      // end caps
            } else {
                level.setBlock(new Vector3(base.x + dx, 150, base.z), org.powernukkitx.block.Block.get(org.powernukkitx.block.BlockID.AIR));
            }
        }
        // Hand-author the steady-state stream (source at depth 0, rising 1 per block downstream)
        // instead of waiting for natural spread: scheduled block updates only run in chunks near
        // players, so a console-run probe would otherwise sit next to an inert source forever.
        // The flow vectors are computed from these depth differences, same as a live stream.
        for (int dx = 0; dx <= 7; dx++) {
            org.powernukkitx.block.Block water = org.powernukkitx.block.Block.get(
                    dx == 0 ? org.powernukkitx.block.BlockID.WATER : org.powernukkitx.block.BlockID.FLOWING_WATER);
            water.setPropertyValue(org.powernukkitx.block.property.CommonBlockProperties.LIQUID_DEPTH, dx);
            level.setBlock(new Vector3(base.x + dx, 150, base.z), water, false, false);
        }
        sender.sendMessage(TextFormat.YELLOW + "[tnttest] stream built at " + base.getFloorX() + ",150," + base.getFloorZ()
                + " flowing +X -- dropping TNT in...");
        Server.getInstance().getScheduler().scheduleDelayedTask(plugin, () -> {
            Vector3 start = new Vector3(base.x + 1.5, 150.2, base.z + 0.5);
            // Fuse is stored as a byte, so 127 is the ceiling; 120 outlives the 100-tick measurement.
            CompoundTag nbt = Entity.getDefaultNBT(start).putByte("Fuse", (byte) 120);
            Entity tnt = Entity.createEntity(EntityID.TNT, level.getChunk(start.getChunkX(), start.getChunkZ(), true), nbt);
            if (tnt == null) {
                sender.sendMessage(TextFormat.RED + "[tnttest] could not spawn the TNT entity!");
                return;
            }
            tnt.spawnToAll();
            Server.getInstance().getScheduler().scheduleDelayedTask(plugin, () -> {
                if (tnt.isClosed()) {
                    sender.sendMessage(TextFormat.RED + "[tnttest] stream TNT vanished before it could be measured.");
                    return;
                }
                double drifted = tnt.x - start.x;
                StringBuilder cells = new StringBuilder();
                for (int dx = 0; dx <= 5; dx++) {
                    cells.append(dx).append('=')
                            .append(level.getBlock(new Vector3(base.x + dx, 150, base.z)).getId().replace("minecraft:", ""))
                            .append(' ');
                }
                sender.sendMessage(TextFormat.GRAY + "[tnttest] tnt at " + String.format("%.2f,%.2f,%.2f", tnt.x, tnt.y, tnt.z)
                        + " channel: " + cells);
                tnt.close();
                if (drifted < 1.0) {
                    sender.sendMessage(TextFormat.RED + String.format("[tnttest] stream only carried the TNT %.2f blocks -- water push is broken.", drifted));
                } else {
                    sender.sendMessage(TextFormat.GREEN + String.format("[tnttest] stream carried the TNT %.1f blocks downstream -- cannon feeding works.", drifted));
                }
            }, 100);
        }, 80);
        return true;
    }

    /**
     * Full water-cannon shot probe: obsidian barrel with a 7-cell stream flowing +X, a charge TNT
     * dropped in the water upstream (it rides the current toward the muzzle while its fuse burns)
     * and a dry projectile TNT on the muzzle pad. Reports where the charge actually detonates,
     * where the projectile is at that moment, and where the projectile lands -- run it a few
     * times to see shot-to-shot variance.
     */
    private boolean shotTest(CommandSender sender, Level level, Vector3 posIn, int charges) {
        final Vector3 base = new Vector3(posIn.getFloorX() + 4, 150, posIn.getFloorZ());
        org.powernukkitx.block.Block obsidian = org.powernukkitx.block.Block.get(org.powernukkitx.block.BlockID.OBSIDIAN);
        for (int dx = -1; dx <= 12; dx++) {
            level.setBlock(new Vector3(base.x + dx, 149, base.z), obsidian.clone(), false, false);
            level.setBlock(new Vector3(base.x + dx, 150, base.z - 1), obsidian.clone(), false, false);
            level.setBlock(new Vector3(base.x + dx, 150, base.z + 1), obsidian.clone(), false, false);
            if (dx == -1) {
                level.setBlock(new Vector3(base.x + dx, 150, base.z), obsidian.clone(), false, false);
            } else {
                level.setBlock(new Vector3(base.x + dx, 150, base.z), org.powernukkitx.block.Block.get(org.powernukkitx.block.BlockID.AIR), false, false);
            }
        }
        for (int dx = 0; dx <= 6; dx++) {
            org.powernukkitx.block.Block water = org.powernukkitx.block.Block.get(
                    dx == 0 ? org.powernukkitx.block.BlockID.WATER : org.powernukkitx.block.BlockID.FLOWING_WATER);
            water.setPropertyValue(org.powernukkitx.block.property.CommonBlockProperties.LIQUID_DEPTH, dx);
            level.setBlock(new Vector3(base.x + dx, 150, base.z), water, false, false);
        }
        armedUntil = System.currentTimeMillis() + 15000;
        pending = sender;
        for (int i = 0; i < charges; i++) {
            spawnTntEntity(level, base.add(4.5, 1.2, 0.5), 60);
        }
        Vector3 projectileStart = base.add(8.5, 1.1, 0.5);
        CompoundTag nbt = Entity.getDefaultNBT(projectileStart).putByte("Fuse", (byte) 100);
        Entity projectile = Entity.createEntity(EntityID.TNT, level.getChunk(projectileStart.getChunkX(), projectileStart.getChunkZ(), true), nbt);
        if (projectile == null) {
            sender.sendMessage(TextFormat.RED + "[tnttest] could not spawn projectile");
            return true;
        }
        projectile.spawnToAll();
        sender.sendMessage(TextFormat.YELLOW + "[tnttest] shot rig at " + base.getFloorX() + ",150," + base.getFloorZ()
                + ": water cells x+0..x+6, " + charges + " charge(s) dropped at x+4.5, projectile dry at x+8.5. Charge pops in 3s:");
        Server.getInstance().getScheduler().scheduleDelayedTask(plugin, () -> {
            if (!projectile.isClosed()) {
                sender.sendMessage(TextFormat.AQUA + String.format("[tnttest] t+65: projectile at %.2f,%.2f,%.2f motion %.2f,%.2f,%.2f",
                        projectile.x, projectile.y, projectile.z, projectile.motionX, projectile.motionY, projectile.motionZ));
            }
        }, 65);
        return true;
    }

    /**
     * Cannon-implosion probe: cobblestone box with a single water source hole (the classic wet
     * charge chamber), then two TNT dropped into the water with fuses a few ticks apart -- the
     * staggered detonation pattern a multi-charge cannon produces. If the first blast throws the
     * second charge out of the water, its dry explosion is what eats cannons.
     */
    private boolean boxTest(CommandSender sender, Level level, Vector3 posIn, int fuseGap, int count) {
        Vector3 base = new Vector3(posIn.getFloorX() + 4, 150, posIn.getFloorZ() + 4);
        org.powernukkitx.block.Block cobble = org.powernukkitx.block.Block.get(org.powernukkitx.block.BlockID.COBBLESTONE);
        // 3x3 footprint of cobble with a 1x1 water hole in the middle, two water sources deep --
        // the classic Java charge chamber; shallow 1-deep water lets juggled charges escape.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    Vector3 p = base.add(dx, dy, dz);
                    if (dx == 0 && dz == 0 && dy >= -1) continue;
                    level.setBlock(p, cobble.clone(), false, false);
                }
            }
        }
        org.powernukkitx.block.Block water = org.powernukkitx.block.Block.get(org.powernukkitx.block.BlockID.WATER);
        level.setBlock(base.add(0, -1, 0), water.clone(), false, false);
        level.setBlock(base, water.clone(), false, false);

        armedUntil = System.currentTimeMillis() + 15000;
        pending = sender;
        Vector3 drop = base.add(0.5, 1.2, 0.5);
        for (int i = 0; i < count; i++) {
            spawnTntEntity(level, drop, 30 + i * fuseGap);
        }
        sender.sendMessage(TextFormat.YELLOW + "[tnttest] cobble water-box built at " + base.getFloorX() + ",150,"
                + base.getFloorZ() + "; " + count + " TNT dropped in, fuse gap " + fuseGap
                + " ticks. Watch the reports:");
        return true;
    }

    private void spawnTntEntity(Level level, Vector3 pos, int fuse) {
        CompoundTag nbt = Entity.getDefaultNBT(pos).putByte("Fuse", (byte) Math.min(fuse, 127));
        Entity tnt = Entity.createEntity(EntityID.TNT, level.getChunk(pos.getChunkX(), pos.getChunkZ(), true), nbt);
        if (tnt != null) {
            tnt.spawnToAll();
        }
    }

    private volatile long armedUntil;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExplode(EntityExplodeEvent event) {
        CommandSender sender = pending;
        if (sender == null) return;
        if (System.currentTimeMillis() > armedUntil) {
            pending = null;
            return;
        }
        if (event.isCancelled()) {
            sender.sendMessage(TextFormat.RED + "[tnttest] explosion was CANCELLED by a plugin -- that's the bug.");
            return;
        }
        int count = event.getBlockList().size();
        String at = String.format("%.2f,%.2f,%.2f", event.getPosition().x, event.getPosition().y, event.getPosition().z);
        if (count == 0) {
            sender.sendMessage(TextFormat.GREEN + "[tnttest] explosion at " + at + " destroyed 0 blocks (in water/mid-air = protected).");
        } else {
            sender.sendMessage(TextFormat.RED + "[tnttest] explosion at " + at + " destroyed " + count + " blocks.");
        }
    }
}

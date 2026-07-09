package org.powernukkitx.entity.item;

import org.powernukkitx.entity.Entity;
import org.powernukkitx.entity.EntityExplosive;
import org.powernukkitx.event.entity.EntityDamageEvent;
import org.powernukkitx.event.entity.EntityDamageEvent.DamageCause;
import org.powernukkitx.event.entity.EntityExplosionPrimeEvent;
import org.powernukkitx.level.Explosion;
import org.powernukkitx.level.GameRule;
import org.powernukkitx.level.Sound;
import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.level.vibration.VibrationEvent;
import org.powernukkitx.level.vibration.VibrationType;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.cloudburstmc.protocol.bedrock.data.actor.ActorDataTypes;
import org.cloudburstmc.protocol.bedrock.data.actor.ActorFlags;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author MagicDroidX
 */
public class EntityTnt extends Entity implements EntityExplosive {
    @Override
    @NotNull
    public String getIdentifier() {
        return TNT;
    }

    protected int fuse;
    protected Entity source;

    public EntityTnt(IChunk chunk, CompoundTag nbt) {
        this(chunk, nbt, null);
    }

    public EntityTnt(IChunk chunk, CompoundTag nbt, Entity source) {
        super(chunk, nbt);
        this.source = source;
    }

    @Override
    public float getWidth() {
        return 0.98f;
    }

    @Override
    public float getLength() {
        return 0.98f;
    }

    @Override
    public float getHeight() {
        return 0.98f;
    }

    @Override
    protected float getDefaultGravity() {
        return 0.04f;
    }

    @Override
    protected float getDrag() {
        return 0.02f;
    }

    @Override
    protected float getBaseOffset() {
        return 0.49f;
    }

    @Override
    public boolean canCollide() {
        return false;
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        return source.getCause() == DamageCause.VOID && super.attack(source);
    }

    @Override

    protected void initEntity() {
        super.initEntity();

        if (this.nbt.contains("Fuse")) {
            fuse = this.getNbt().getByte("Fuse");
        } else {
            fuse = 80;
        }

        this.setDataFlag(ActorFlags.IGNITED, true);
        this.setDataProperty(ActorDataTypes.FUSE_TIME, fuse);

        this.getLevel().addSound(this, Sound.RANDOM_FUSE);
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    protected boolean requiresPreciseMovementSync() {
        // Cannoning relies on the client seeing the exact same position/motion the server is
        // simulating every single tick; the generic entity movement dead-zone is fine for mobs
        // but throws off manually-timed factions TNT cannons.
        return true;
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.nbt.putByte("Fuse", (byte) fuse);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (closed) {
            return false;
        }

        int tickDiff = currentTick - lastUpdate;

        if (tickDiff <= 0 && !justCreated) {
            return true;
        }

        // Send the fuse countdown every tick (upstream throttled this to every 5th tick),
        // since cannon players read the exact fuse value to time chained detonations.
        this.setDataProperty(ActorDataTypes.FUSE_TIME, fuse);

        lastUpdate = currentTick;

        boolean hasUpdate = entityBaseTick(tickDiff);

        if (isAlive()) {

            motionY -= getGravity();

            move(motionX, motionY, motionZ);

            float friction = 1 - getDrag();

            motionX *= friction;
            motionY *= friction;
            motionZ *= friction;

            updateMovement();

            if (onGround) {
                motionY *= -0.5;
                motionX *= 0.7;
                motionZ *= 0.7;
            }

            fuse -= tickDiff;

            if (fuse <= 0) {
                if (this.level.getGameRules().getBoolean(GameRule.TNT_EXPLODES)) {
                    explode();
                }
                kill();
            }

        }

        return hasUpdate || fuse >= 0 || Math.abs(motionX) > 0.00001 || Math.abs(motionY) > 0.00001 || Math.abs(motionZ) > 0.00001;
    }

    @Override
    public void explode() {
        EntityExplosionPrimeEvent event = new EntityExplosionPrimeEvent(this, 4);
        server.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        Explosion explosion = new Explosion(this, event.getForce(), this);
        explosion.setFireChance(event.getFireChance());
        if (event.isBlockBreaking()) {
            explosion.explodeA();
        }
        explosion.explodeB();
        this.level.getVibrationManager().callVibrationEvent(new VibrationEvent(this, this.getVector3(), VibrationType.EXPLODE));
    }

    public Entity getSource() {
        return source;
    }

    @Override
    public String getOriginalName() {
        return "Block of TNT";
    }

    @Override
    public Set<String> typeFamily() {
        return Set.of("tnt", "inanimate");
    }
}

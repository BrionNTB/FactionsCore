# TNT cannon timing/physics patch

Factions PvP servers live and die on TNT cannons: players build machines that use chained
detonations to fling players/loot across the map, and the whole thing only works if the timing is
precise and repeatable.

## What was wrong

PowerNukkitX's generic entity movement sync (`Entity.updateMovement()`) gates position/motion
packets behind a "did it move enough to bother telling the client" dead-zone (`diffPosition >
0.0001`, `diffMotion > 0.0025`). That's a sensible bandwidth optimization for mobs and item drops,
but it means a primed TNT entity's *visible* position can lag a tiny amount behind its *simulated*
position tick to tick. Cannon timing is read visually by the player (watching the TNT's position to
know when to trigger the next stage), so any client/server drift here directly breaks cannon math,
even though the server's own physics simulation was already fine.

The per-tick fuse countdown sent to the client (`ActorDataTypes.FUSE_TIME`) was also throttled to
every 5th tick, which is fine for the visual "sparks" effect but not precise enough for players
timing multi-TNT chains off the exact fuse value.

## What changed

- `entity/Entity.java`: added `requiresPreciseMovementSync()` (default `false`); when an entity
  overrides it to `true`, `updateMovement()` sends every position/motion change immediately instead
  of applying the dead-zone.
- `entity/item/EntityTnt.java`, `EntityTntMinecart.java`, `EntityFallingBlock.java`: override it to
  return `true` — these are exactly the entities factions cannons are built from (primed TNT, TNT
  minecarts, and falling sand/gravel/anvil cannons).
- `EntityTnt.java`: fuse countdown is now sent to the client every tick instead of every 5th.

Nothing about the actual physics simulation (gravity, drag, explosion blast-radius algorithm)
needed changing — `level/Explosion.java` already faithfully ports vanilla's 16-ray-per-face
raycasting algorithm, including its intentional randomized blast-force falloff, which is what gives
vanilla-accurate (and cannon-predictable) block destruction patterns. The fix here is entirely
about closing the gap between what the server simulates and what the client is shown, tick for
tick.

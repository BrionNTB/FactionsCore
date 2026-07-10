# FactionsCore

A PvP-focused Minecraft Bedrock server, built as a fork of
[PowerNukkitX](https://github.com/PowerNukkitX/PowerNukkitX) with the classic Java 1.8.8-era
Factions gamemode layered on top.

## Why PowerNukkitX and not the official Bedrock Dedicated Server?

Mojang/Microsoft ship Bedrock Dedicated Server (BDS) as a closed-source binary only — there is no
source to fork or patch. PowerNukkitX is an open-source, from-scratch Java reimplementation of the
Bedrock protocol, so it's the actual buildable foundation for "fork the server and change how its
physics/combat/anti-cheat work." See `engine/FORK_NOTES.md` for details and the exact upstream
commit this was forked from.

## Layout

```
engine/powernukkitx/    Vendored + patched fork of PowerNukkitX (the server core)
plugins/FactionsCore/   The gamemode: factions, custom enchants, anti-cheat, stacking, etc.
```

This is a Gradle composite build: `engine/powernukkitx` builds as its own project (so pulling
upstream PowerNukkitX changes stays a clean merge), and `plugins/FactionsCore` depends on it like
any other PowerNukkitX plugin would.

## Building

```
./gradlew :plugins:FactionsCore:shadowJar
```

Produces `plugins/FactionsCore/build/libs/FactionsCore-<version>.jar`. Run the engine
(`engine/powernukkitx`) as your server and drop that jar in its `plugins/` folder, or build the
engine itself (`./gradlew :powernukkitx:build` from within `engine/powernukkitx`) per PowerNukkitX's
own instructions.

## What's implemented, and where

| Request | Where |
|---|---|
| Forked/optimized Bedrock server core | `engine/powernukkitx` (patched PowerNukkitX) |
| TNT cannon timing/physics responsiveness | Engine patch: `entity/Entity.java` (`requiresPreciseMovementSync`), `entity/item/EntityTnt.java`, `EntityTntMinecart.java`, `EntityFallingBlock.java`. Details in `docs/TNT_PHYSICS.md`. |
| Java 1.8.8-style PvP | Bedrock never had Java's 1.9+ attack-cooldown system, so combat is already structurally 1.8-like; tunables (knockback, critical hits, sprint-reset) in `plugins/FactionsCore/.../faction/listener/CombatTuningListener.java` and `config.yml` under `combat:`. |
| Factions gamemode | `plugins/FactionsCore/.../faction/` — claims, power, home, ally/enemy/truce, safezone/warzone, `/f` and `/fadmin` commands. |
| Custom enchants (CosmicPvP-style), up to Protection VI / Sharpness VI | `plugins/FactionsCore/.../enchant/` — Stun, Tornado, Leech, Blaze, Spring, Haste, Escape, Lightning, Gravity, Webber, Protection (I-VI), Sharpness (I-VI). Registered through PowerNukkitX's own custom-enchant framework for storage/lore/compatibility checks; proc logic in `EnchantCombatListener.java`. |
| Enchant books: drag-to-apply, no anvil, own item IDs, success/fail | `plugins/FactionsCore/.../enchant/book/` (12 book items, e.g. `factionscore:book_lightning`) + `EnchantApplyListener.java`. See "Enchant books" below. |
| Anti-cheat (autoclicker/x-ray/aimbot) | `plugins/FactionsCore/.../anticheat/` — heuristic checks (CPS + click-rhythm variance, blind-ore-break clustering, aim snap-angle + jitter variance), flag storage, staff alerts, escalating punishment. See caveats below. |
| Auto-updates for newest Bedrock version | `plugins/FactionsCore/.../updater/AutoUpdater.java` — polls GitHub releases, stages the new jar; requires the server to run under a process supervisor that restarts on exit (systemd, a restart loop, pm2, ...) to actually apply it hands-off. |
| Mob stacking / spawner stacking | `plugins/FactionsCore/.../stacking/` — same-type mobs merge into one entity with a live count; sneak+right-click a spawner to raise its stack size (mobs/cycle + nearby cap). Required a small engine patch (`BlockEntityMobSpawner` had no public setter for spawn count). |
| Buycraft/Tebex integration | `plugins/FactionsCore/.../buycraft/BuycraftIntegration.java` — polls Tebex's Command Queue API and executes queued purchase commands on console. **Verify the endpoint/payload shape against Tebex's current docs before going live** — it was implemented from the documented command-queue contract, not tested against a live store. |
| Faction "value" (spawners count toward it) | `plugins/FactionsCore/.../faction/FactionValueManager.java` — value = claimed chunks + tracked spawners, each spawner's contribution scaling with its stack level (persisted per-block so stacking/breaking/restarts stay accurate without rescanning). `/f value`, `/f top`. |
| Reach check | `plugins/FactionsCore/.../anticheat/check/ReachCheck.java` — flags hits beyond vanilla melee range (eye-to-hitbox distance, not center-to-center). |
| World border (60k, all 3 dimensions) | `plugins/FactionsCore/.../border/` — hard movement clamp (can't walk/fly/swim past it), explosions and PvP are no-ops beyond it, a `BLOCK_FORCE_FIELD` particle wall + popup renders near the border, and a periodic sweep recovers anyone who got past it by other means (teleport to spawn) — except combat-tagged players, who are never auto-teleported (see combat log below) and are simply held at the wall instead. `/worldborder info`. |
| Combat log (10s kill-on-logout, no-teleport while tagged) | `plugins/FactionsCore/.../combat/` — `CombatTagManager` tags both players on a PvP hit for `combatlog.tag-duration-seconds` (10s default); `CombatLogListener` kills a tagged player who disconnects (drops inventory, applies the faction death-power-loss, broadcasts it) and cancels `PlayerTeleportEvent` for tagged players (commands, ender pearls, etc.) — the world-border wall-correction teleport is exempted since it's a server-side position correction, not a player-facing teleport. |
| Classic (1.8.8-style) cave systems | Engine patch: `level/generator/stages/normal/ClassicCaveCarvingStage.java`. See "World generation" below — PowerNukkitX already ships the exact vanilla worm-tunnel/room carving algorithm (`CaveGenerateFeature`), it just isn't wired into any biome's feature list; this stage revives it as an opt-in layer on top of the modern noise caves. |
| Water/lava flow, cobblestone generators, redstone | Verified by source review, not live testing (no way to drive a Bedrock client from this environment) — see "Verified engine behavior" below. Water/lava/cobblestone needed no changes; redstone had one real gap (a dead config flag), now fixed, plus a startup diagnostic. |
| Hopper + slime block (piston-moved hoppers keep their contents) | Verified by source review — `BlockPistonBase`'s moving-block system already saves/restores a pushed block's full BlockEntity (inventory included) across the move, so a hopper dragged by a slime-block piston train doesn't lose its items. No changes needed. |
| Tiered custom hoppers (I vanilla, II = 1.5x loot, III = 3x loot) | Engine patch: `blockentity/BlockEntityHopper.java` (tier field + multiplier applied on pickup). Plugin: `plugins/FactionsCore/.../hopper/` — `factionscore:hopper_tier2` / `factionscore:hopper_tier3`, glinting custom items that place a real hopper block tagged with the tier. See "Custom hoppers" below. |
| `/shop`, `/setshop`, `/sell` with a fluctuating-demand economy | `plugins/FactionsCore/.../shop/` — `/setshop` (admin) records a location, `/shop` teleports there (blocked while combat-tagged, like any other teleport), `/sell` opens a chest-style GUI of configured mob-loot/crop-loot prices; demand multipliers random-walk every 4h (configurable) and a lightweight per-player $ balance is tracked in the plugin's own database. |

## Verified engine behavior (water/lava/cobblestone/redstone)

These were investigated by reading the vendored engine source, not by connecting a client — there's
no way to drive real Bedrock protocol gameplay from this environment. What was found:

- **Water/lava flow**: `block/BlockLiquid.java` implements standard spread/depth-decay flow, and
  the classic "two adjacent water sources create a new source" infinite-water rule
  (`adjacentSources >= 2`).
- **Cobblestone generators**: `block/BlockFlowingLava.java#checkForMixing()` /
  `#flowIntoBlock()` faithfully reproduce vanilla's lava+water contact rules: source lava + water →
  obsidian, flowing lava + water → cobblestone, lava flowing *into* a water block → stone. A
  standard water+lava cobblestone generator works exactly like vanilla.
- **Redstone**: `block/BlockRedstoneWire.java`, `BlockRedstoneTorch`/`BlockUnlitRedstoneTorch`,
  `BlockRedstoneDiode` (repeater base), `BlockRedstoneComparator`, `BlockObserver`, and
  `BlockPistonBase` are all complete, faithful ports of vanilla logic (wire propagation including
  the slab upward/downward edge cases; repeater locking; comparator subtract/compare modes and
  container-signal-strength reads; observer pulse-on-neighbor-change; sticky-piston block groups
  correctly excluding slime-to-honey adjacency). The one real gap found: `gameplay-settings.tick-
  redstone` was declared and read from config but never actually checked anywhere — a dead toggle.
  It's now wired into every component's scheduled-tick branch, so it does what its name says
  (freezes redstone updates without disabling components outright, unlike `enable-redstone`).
  `FactionsCorePlugin` also logs a loud warning at startup if either setting is off, since
  "redstone doesn't work" is far more often a disabled gameplay setting than an engine bug — the
  reputation for broken redstone comes from old Nukkit-1/CloudburstMC-era forks; PowerNukkitX
  already fixed this.
- **Hopper + slime blocks**: pushing a hopper with a sticky-piston/slime-block train preserves its
  inventory across the move (`BlockPistonBase` stashes the block entity's saved NBT in the
  temporary moving-block entity and restores it at the destination) — verified by tracing that
  round-trip in `BlockPistonBase.java`/`BlockEntityPistonArm.java`.

## World generation: classic cave systems

`gameplay-settings.classic-caves` (default `false`, alongside `enable-redstone` etc. in the
server's settings file) turns on `ClassicCaveCarvingStage`, which runs PowerNukkitX's already-built
`CaveGenerateFeature`/`CaveExtraUndergroundFeature` — a faithful port of vanilla's pre-1.18
`MapGenCaves` random-walk tunnel-and-room carver — right after terrain shaping, on every overworld
chunk. It's additive on top of the modern density-function noise caves (`NormalTerrainStage`)
rather than a replacement: fully suppressing the modern noise caves would mean unpicking cave
carving out of the density-function/aquifer system, which is deeply entangled and too risky to
attempt as a fork patch. With `classic-caves` on, worlds get vanilla-1.8-style winding tunnels and
occasional rooms layered into the modern terrain. Performance-wise this matches vanilla's own
historical cost profile (an 8-chunk-radius reseed per chunk) — it's not cheap, but it's the same
cost real Minecraft always paid for this algorithm, not a regression.

## Enchant books: drag-to-apply, no anvil

Each custom enchant ships as one item id (e.g. `factionscore:book_lightning`, glinting, 12 total
including the new Protection/Sharpness lines) with its level stored in the book's own NBT rather
than baked into the item id — `/enchant givebook <player> <enchant> <level>` spawns one. Applying
it is a drag: drop the book onto the target item in your own inventory. That gesture is a vanilla
"swap two dissimilar stacks" action, which PowerNukkitX already surfaces to plugins as
`PlayerTransferItemEvent` (type `SWAP`) with both items and their resolved inventories/slots —
`EnchantApplyListener` only ever intervenes when the source is specifically one of these book items
and the destination is a valid target for that enchant type; every other drag/swap in the game
passes through untouched, so this can't affect ordinary inventory management. No anvil, no new
UI, no raw protocol parsing.

Success isn't guaranteed (`enchants.apply.*` in config.yml):
- Base success chance drops as the book's level goes up (e.g. 90% at level 1 down to 25% at level
  6 by default).
- Dragging a **stack of 2+** of the same book consumes both and rolls against the higher
  "combined" chance instead — "combining two of the same books" to shorten the odds.
- Higher-level books require a minimum player (XP) level to even attempt.
- On failure the book(s) are still consumed and the player takes real damage
  (`enchants.apply.fail-damage`), plus a themed backfire for a few enchants with an obvious
  self-directed version of their effect (Lightning strikes the player for real, Blaze sets them on
  fire, Stun slows/blinds them); everything else just takes the base damage.

## Custom hoppers

`factionscore:hopper_tier2` / `factionscore:hopper_tier3` place a real vanilla hopper block (so
existing redstone-lock/transfer-cooldown/item-transfer logic applies unchanged) tagged with a tier
in its block entity's NBT. The tier multiplies the item count of whatever the hopper vacuums off
the ground before it lands in its inventory — 1.5x for tier II, 3x for tier III — which then flows
normally through however the hopper pipes it onward (into a chest, into the next hopper, etc.), so
"deposits more to the next hopper/chest" falls out of the pickup-side multiplier rather than being
reimplemented at every hand-off. It multiplies whatever the hopper picks up, not specifically
items tagged as mob-kill drops — distinguishing an item's origin would need tagging every dropped
item at the point it's spawned, which is out of scope here. Breaking a tiered hopper drops the
correct tier item back (`HopperTierListener` overrides `BlockBreakEvent`'s drops), so it round-trips
correctly.

## Anti-cheat caveats (read before relying on this in production)

Server-side anti-cheat for a vanilla (unmodified) Bedrock client is fundamentally a game of
statistics, not proof — there's no client-integrity signal to check against. The checks here
(`anticheat/check/`: autoclicker, x-ray, aim, reach) are heuristics tuned to be conservative (see
`config.yml`'s `anticheat:` block for thresholds) and are meant to flag-and-alert-staff by default,
escalating to kick/ban only after repeated flags. Expect to tune the thresholds against your own
player base before trusting auto-punishment.

## Config

All tunables live in `plugins/FactionsCore/src/main/resources/config.yml`, copied to the plugin's
data folder on first run — including the `border:` (radius per dimension, warning distance, wall
rendering) and `combatlog:` (tag duration, whether to block teleports) sections. The one setting
that lives outside the plugin, because it's an engine/world-generation feature rather than
gameplay logic, is `gameplay-settings.classic-caves` in the server's own settings file (see "World
generation" above).

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
| Custom enchants (CosmicPvP-style) | `plugins/FactionsCore/.../enchant/` — Stun, Tornado, Leech, Blaze, Spring, Haste, Escape, Lightning, Gravity, Webber. Registered through PowerNukkitX's own custom-enchant framework (auto-generated enchanted books, anvil combining, lore); proc logic in `EnchantCombatListener.java`. |
| Anti-cheat (autoclicker/x-ray/aimbot) | `plugins/FactionsCore/.../anticheat/` — heuristic checks (CPS + click-rhythm variance, blind-ore-break clustering, aim snap-angle + jitter variance), flag storage, staff alerts, escalating punishment. See caveats below. |
| Auto-updates for newest Bedrock version | `plugins/FactionsCore/.../updater/AutoUpdater.java` — polls GitHub releases, stages the new jar; requires the server to run under a process supervisor that restarts on exit (systemd, a restart loop, pm2, ...) to actually apply it hands-off. |
| Mob stacking / spawner stacking | `plugins/FactionsCore/.../stacking/` — same-type mobs merge into one entity with a live count; sneak+right-click a spawner to raise its stack size (mobs/cycle + nearby cap). Required a small engine patch (`BlockEntityMobSpawner` had no public setter for spawn count). |
| Buycraft/Tebex integration | `plugins/FactionsCore/.../buycraft/BuycraftIntegration.java` — polls Tebex's Command Queue API and executes queued purchase commands on console. **Verify the endpoint/payload shape against Tebex's current docs before going live** — it was implemented from the documented command-queue contract, not tested against a live store. |
| Faction "value" (spawners count toward it) | `plugins/FactionsCore/.../faction/FactionValueManager.java` — value = claimed chunks + tracked spawners, each spawner's contribution scaling with its stack level (persisted per-block so stacking/breaking/restarts stay accurate without rescanning). `/f value`, `/f top`. |
| Reach check | `plugins/FactionsCore/.../anticheat/check/ReachCheck.java` — flags hits beyond vanilla melee range (eye-to-hitbox distance, not center-to-center). |
| World border (60k, all 3 dimensions) | `plugins/FactionsCore/.../border/` — hard movement clamp (can't walk/fly/swim past it), explosions and PvP are no-ops beyond it, a `BLOCK_FORCE_FIELD` particle wall + popup renders near the border, and a periodic sweep recovers anyone who got past it by other means (teleport to spawn) — except combat-tagged players, who are never auto-teleported (see combat log below) and are simply held at the wall instead. `/worldborder info`. |
| Combat log (10s kill-on-logout, no-teleport while tagged) | `plugins/FactionsCore/.../combat/` — `CombatTagManager` tags both players on a PvP hit for `combatlog.tag-duration-seconds` (10s default); `CombatLogListener` kills a tagged player who disconnects (drops inventory, applies the faction death-power-loss, broadcasts it) and cancels `PlayerTeleportEvent` for tagged players (commands, ender pearls, etc.) — the world-border wall-correction teleport is exempted since it's a server-side position correction, not a player-facing teleport. |
| Classic (1.8.8-style) cave systems | Engine patch: `level/generator/stages/normal/ClassicCaveCarvingStage.java`. See "World generation" below — PowerNukkitX already ships the exact vanilla worm-tunnel/room carving algorithm (`CaveGenerateFeature`), it just isn't wired into any biome's feature list; this stage revives it as an opt-in layer on top of the modern noise caves. |
| Water/lava flow, cobblestone generators, redstone | Verified by source review, not live testing (no way to drive a Bedrock client from this environment) — see "Verified engine behavior" below. All three were already correctly implemented; no engine changes were needed. |

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
- **Redstone**: `block/BlockRedstoneWire.java` is a complete, faithful port of vanilla's wire
  propagation (including the slab upward/downward propagation edge cases), and it's gated behind
  `gameplay-settings.enable-redstone`, which **defaults to `true`**. Pistons, repeaters,
  comparators, torches, etc. are all implemented. If redstone still doesn't work on your server,
  check that setting hasn't been turned off, rather than assuming the engine is at fault — the
  reputation of "redstone doesn't work" comes from old Nukkit-1/CloudburstMC-era forks; PowerNukkitX
  is a different, actively-maintained fork that already fixed this.

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

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

## Anti-cheat caveats (read before relying on this in production)

Server-side anti-cheat for a vanilla (unmodified) Bedrock client is fundamentally a game of
statistics, not proof — there's no client-integrity signal to check against. The checks here
(`anticheat/check/`) are heuristics tuned to be conservative (see `config.yml`'s
`anticheat:` block for thresholds) and are meant to flag-and-alert-staff by default, escalating to
kick/ban only after repeated flags. Expect to tune the thresholds against your own player base
before trusting auto-punishment.

## Config

All tunables live in `plugins/FactionsCore/src/main/resources/config.yml`, copied to the plugin's
data folder on first run.

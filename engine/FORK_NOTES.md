# FactionsCore Engine Fork

`engine/powernukkitx` is a vendored fork of
[PowerNukkitX](https://github.com/PowerNukkitX/PowerNukkitX), an open-source,
Java-based reimplementation of the Minecraft Bedrock protocol (Nukkit
lineage).

## Why not fork the official Bedrock Dedicated Server (BDS)?

Mojang/Microsoft only distribute BDS as a closed-source compiled binary —
there is no source release to fork. That means true server-side changes to
things like TNT/cannon physics timing, combat knockback formulas, or a
from-source anti-cheat are not achievable by patching Mojang's binary.
PowerNukkitX implements the Bedrock protocol from scratch in open Java
source, so it can actually be forked and modified the way this project
needs — full control over ticking, entity physics, combat resolution, and
plugin hooks, while still speaking to real Bedrock clients.

## Provenance

- Upstream: `https://github.com/PowerNukkitX/PowerNukkitX`
- Vendored at upstream commit: `7df5794efc273c9ee301f169422d03f060805b13`
  (2026-07-09)
- License: unchanged, see `powernukkitx/LICENSE`

## What's patched here vs. what lives in the plugin

Low-level engine changes that need access to core tick/physics/networking
internals are patched directly in `powernukkitx/src`:

- TNT fuse/motion tick responsiveness (`entity/item/EntityTnt.java`,
  `level/Explosion.java`) — see `docs/TNT_PHYSICS.md` at the repo root.
- Legacy (Java 1.8.8-style) combat knockback formula, config-toggleable.

Everything else (Factions, custom enchants, anti-cheat, mob/spawner
stacking, auto-updater, Buycraft/Tebex) is a normal plugin —
`plugins/FactionsCore` — built against this engine's plugin API. Keeping
those as a plugin instead of engine patches means they can be
updated/rebuilt without re-patching the fork every time upstream moves.

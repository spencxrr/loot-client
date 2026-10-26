# Loot Client

A Fabric utility client for Minecraft **1.21.11**, made for anarchy servers where every hack is allowed.
Don't use it on servers with rules against it; it has no anticheat bypasses and will get you banned there.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for 1.21.11.
2. Drop `loot-client-x.y.z.jar` and [Fabric API](https://modrinth.com/mod/fabric-api) into `.minecraft/mods`.
3. For real FPS gains, also add [Sodium](https://modrinth.com/mod/sodium) and [Lithium](https://modrinth.com/mod/lithium). Loot Client doesn't touch chunk rendering, so it works alongside them.

## Controls

- **Right Shift** opens the click GUI. Left click toggles, right click shows settings, middle click binds a key (Esc/Backspace clears).
- Chat commands (never sent to the server): `.help`, `.modules`, `.t <module>`, `.bind <module> <key|none>`,
  `.settings <module>`, `.set <module> <setting> <value>`, `.bases [clear]`, `.save`.
- Config lives in `.minecraft/config/lootclient.json`.

## Modules

| Category | Module | What it does |
|---|---|---|
| Render | HUD | Watermark, module list, coords with nether/overworld conversion, FPS, ping |
| Render | Fullbright | Client-side night vision |
| Render | NoRender | Skip particles, weather, hurt cam |
| Render | ESP | Boxes around players/mobs/items through walls |
| Render | Tracers | Lines from crosshair to entities, colored by distance |
| Render | StorageESP | Highlights chests, shulkers, ender chests, barrels, etc. |
| Render | BlockESP | Highlights any blocks you choose (`.set BlockESP Blocks add ancient_debris`) |
| World | BaseFinder | Scores chunks for player-made stuff (shulkers, ender chests, beacons, signs, item frames...), alerts in chat with click-to-copy coords, logs to `.minecraft/lootclient/bases-<server>.txt` |
| Player | AutoTotem | Keeps a totem in your offhand |
| Player | NoFall | No fall damage |
| Player | Velocity | Scale or cancel knockback |
| Movement | Sprint | Always sprint |
| Movement | Step | Step up full blocks |
| Movement | Flight | Creative-style flight (needs `allow-flight=true` on the server) |

## Build

```
./gradlew build
```

The jar ends up in `build/libs/`. Every push also builds on GitHub Actions; grab the jar from the run's artifacts.

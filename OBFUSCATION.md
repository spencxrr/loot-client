# Release obfuscation

Loot Client ships two builds from one codebase:

| Build | Command | Jar | Readable? |
|-------|---------|-----|-----------|
| Debug | `./gradlew build` | `build/libs/loot-client-<ver>.jar` | yes (for development) |
| Release | `./gradlew build -Prelease` | `build/libs/loot-client-<ver>.jar` | obfuscated |

CI builds both on every push and uploads them as the **loot-client-debug** and
**loot-client-release** artifacts. The release artifact also contains
`mapping.txt` (keep it private - see below).

## How it works

The release build inserts a [ProGuard](https://www.guardsquare.com/proguard)
pass between compilation and Loom's remap step:

```
compile (yarn-named) ──► ProGuard (rename + strip) ──► Loom remapJar (intermediary) ──► release jar
```

ProGuard runs **before** the remap on purpose. At that point the full
Minecraft + Fabric class hierarchy is on the classpath, so ProGuard can tell
which of our methods override Minecraft methods (e.g. `Screen#render`) and
leaves those names alone. Renaming an override would break the mod silently.

What the release pass does, from `release.pro`:

- **Renames** every internal class, method and field to meaningless names.
- **Strips** `SourceFile`, line numbers and local-variable tables, so a
  decompiler gets no line numbers and no original variable names.

What it deliberately **keeps** so the mod still loads:

- `dev.loot.client.LootClient` (named in `fabric.mod.json`) + its entry methods.
- The whole `dev.loot.client.mixin` package (mixins are matched by name at
  load time via the refmap; renaming them breaks injection).
- Enum `values()`/`valueOf()`.
- `fabric.mod.json`, `lootclient.mixins.json`, the refmap and all assets.

## What this actually buys you (honest version)

This is a **client-side** mod: it runs entirely on the user's machine, so a
determined person with a decompiler can always reverse it eventually. There is
no such thing as an uncrackable client. What obfuscation does is raise the
cost:

- **Casual "skids"** who just decompile-and-rename-package get an unreadable
  wall of `a.b(c)` with no line numbers. That stops most copy-paste reuse.
- **Serious reversers** can still recover behaviour with effort. Renaming
  slows them down; it does not stop them.

So: good deterrent against low-effort theft, not a guarantee.

## Limits and tradeoffs you should know

- **String literals are NOT encrypted.** Module names (`"Auto Crystal"`), chat
  command text, URLs, etc. are still readable with `strings`. String
  encryption was left out on purpose: the free tools that do it are fragile
  with Fabric + mixins and frequently trip antivirus false-positives on a
  sideloaded jar, which would hurt a public release more than it helps. It can
  be added as an opt-in later, with in-game testing. Same goes for
  control-flow flattening and code optimization (`-dontoptimize` is set for
  safety).
- **Harder debugging.** User crash reports from the release jar will show
  obfuscated names. Use `mapping.txt` to translate them back (below).
- **Not runtime-tested in CI.** CI proves the jar builds and that the entry
  point + mixins survive with their names, but it does not launch Minecraft.
  **Test the release jar in-game before publishing a release** - that's the
  only way to confirm every mixin still applies.

## Deobfuscating a crash report

Each release build writes `build/release/mapping.txt` (old name -> new name).
Keep it private and save one copy per release. To turn an obfuscated stack
trace back into real names:

```
retrace mapping.txt crash.txt
```

(`retrace` ships with ProGuard.) Without the matching `mapping.txt` you can't
deobfuscate, so don't lose it and don't ship it to users.

## Turning the knobs

All of it lives in `release.pro`. To experiment (and then test in-game):

- Remove `-dontoptimize` to enable ProGuard's optimizer (inlining, dead-code
  removal, light control-flow changes).
- Remove `-dontshrink` to strip unused code.

Keep rules only ever need adding if you introduce reflection, a new entry
point, or a new mixin package.

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

The release build inserts two obfuscation passes between compilation and Loom's
remap step:

```
compile (yarn-named) ──► ProGuard (rename + strip) ──► StringObf (encrypt strings) ──► Loom remapJar (intermediary) ──► release jar
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

### String encryption (`tools/StringObf.java`)

ProGuard renames code but leaves every string literal in plaintext, and those
strings (module names, setting labels, block names, serialization keys) are
enough to re-label the whole renamed codebase by hand. The second pass, an ASM
transform, fixes that: for each string literal in our own non-mixin classes it
replaces `LDC "text"` with `LDC "<encrypted>"` + a call to a generated
`dev.loot.client.Decoder`, so the constant pool no longer carries readable
strings. On the current build this encrypts ~1000 strings across ~106 classes.

Why it is safe to load:

- It only inserts a `String -> String` call where a `String` was already on the
  stack, so the operand-stack shape is unchanged and the existing stack-map
  frames stay valid. It uses ASM `COMPUTE_MAXS` only, never `COMPUTE_FRAMES`, so
  it never needs to resolve Minecraft types (resolving them with an incomplete
  classpath is what produced the earlier load-time `VerifyError`). CI runs the
  real JVM verifier over the result and fails the build on any `VerifyError`.
- The decoder `intern()`s its result, so a decoded string is the same instance
  as an equal literal elsewhere - any `str == "literal"` identity check in the
  code keeps working.
- The runtime value is identical to the original, so config keys, reflection by
  name and enum names are unaffected.
- **Mixins are skipped** (Mixin matches classes/members by name and reads
  annotation + refmap strings), so their strings remain readable - acceptable,
  since the mixin package is kept readable anyway.

## What this actually buys you (honest version)

This is a **client-side** mod: it runs entirely on the user's machine, so a
determined person with a decompiler can always reverse it eventually. There is
no such thing as an uncrackable client. What obfuscation does is raise the
cost:

- **Casual "skids"** who just decompile-and-rename-package get an unreadable
  wall of `a.b(c)` with no line numbers and no readable strings to orient from.
  That stops most copy-paste reuse.
- **Serious reversers** can still recover behaviour with effort. Renaming and
  string encryption slow them down and force them to run the code to recover
  strings; they do not stop them.

This was measured, not guessed: CI decompiles our own shipped jar on every build
(the "Self-crack analysis" step) so the real protection level is visible in the
log. Before string encryption, the decompile printed every setting and block
name in plaintext. After it, those are gibberish and the decompiler even fails
outright on some methods.

So: good deterrent against low-effort theft, not a guarantee.

## Limits and tradeoffs you should know

- **Logic is not hidden.** Renaming and string encryption hide *names* and
  *strings*, not control flow. A decompiler still reconstructs the shape of each
  method, so a determined reverser can follow what the code does - they just
  have to label it themselves. Control-flow flattening would raise that bar but
  is high-risk (it rewrites method bodies and needs real in-game testing) and is
  not enabled. `-dontoptimize`/`-dontshrink` are set for the same safety reason.
- **Some strings still leak.** Java records store their component-name list
  (e.g. `"speed;pitchOffset;gravity;..."`) in an `invokedynamic` bootstrap, not
  a string constant, so StringObf cannot touch it without breaking the record;
  mixin strings are also left readable on purpose. These give structural hints
  but no longer a labeled map of the client.
- **Harder debugging.** User crash reports from the release jar will show
  obfuscated names. Use `mapping.txt` to translate them back (below).
- **Load is CI-verified; behaviour is not.** CI runs the real JVM bytecode
  verifier over the final obfuscated + string-encrypted classes and fails on any
  `VerifyError`, so "it loads" is proven automatically. CI does **not** launch
  Minecraft, so it cannot prove every feature still behaves. **Smoke-test the
  release jar in-game before publishing** - especially config save/load,
  keybinds and anything that compares strings.

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

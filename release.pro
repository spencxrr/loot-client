# ============================================================================
#  Loot Client - release obfuscation rules (ProGuard)
#
#  This runs on the compiled, yarn-NAMED client jar produced by the `jar`
#  task, BEFORE Loom's `remapJar` converts it to intermediary mappings.
#  Running pre-remap is deliberate: at this point the full Minecraft +
#  Fabric class hierarchy is on the library classpath, so ProGuard can see
#  which of our methods override Minecraft methods (e.g. Screen#render) and
#  will never rename them. Renaming an override would silently break the mod.
#
#  What this does:  renames internal classes/methods/fields to meaningless
#                   names, and strips SourceFile / line-number / local-variable
#                   debug tables (everything not kept below is dropped).
#  What this avoids: shrinking, optimization, control-flow flattening and
#                   string encryption. Those add breakage and antivirus-
#                   false-positive risk on a sideloaded mod and need in-game
#                   testing before they're worth turning on. See OBFUSCATION.md.
# ============================================================================

# Rename only. Do not remove or rewrite code - lowest risk for an untested jar.
-dontshrink
-dontoptimize

# Keep ONLY the attributes Fabric Loader and the Mixin subsystem actually read.
# Dropping the rest erases SourceFile, LineNumberTable, LocalVariableTable and
# LocalVariableTypeTable, so decompiled output has no line numbers or real
# local-variable names.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,Record,PermittedSubclasses

# Quieter output.
-dontnote

# Suppress "can't find referenced class" warnings. These come from Minecraft's
# own transitive libraries (guava, netty, fastutil, datafixers, brigadier, ...)
# referencing each other, not from our code - our code compiled cleanly and its
# own superclasses (Screen, ClientModInitializer, enums) ARE on the classpath,
# so override detection is unaffected. Without this ProGuard aborts with
# "Please correct the above warnings first." We only rename (no shrink/optimize),
# so unresolved library internals can't change our output.
-dontwarn

# ---------------------------------------------------------------------------
#  Fabric entry point - fabric.mod.json references this class by its literal
#  fully-qualified name, so the class name and the two entry members must stay.
# ---------------------------------------------------------------------------
-keep public class dev.loot.client.LootClient {
    public <init>();
    public void onInitializeClient();
}

# ---------------------------------------------------------------------------
#  Mixins - lootclient.mixins.json lists these by package + simple name, and
#  the Mixin service matches @Shadow / @Inject / accessor members by name at
#  load time via the refmap. Renaming any of it breaks injection, so keep the
#  whole mixin package (classes AND members) untouched.
# ---------------------------------------------------------------------------
-keep class dev.loot.client.mixin.** { *; }

# ---------------------------------------------------------------------------
#  Enums - preserve the synthetic values()/valueOf() so name()/valueOf() keep
#  working even though no code currently relies on enum constant names.
# ---------------------------------------------------------------------------
-keepclassmembers enum dev.loot.client.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Resource files (fabric.mod.json, lootclient.mixins.json, the refmap, assets)
# are passed through unchanged - we never enable -adaptresource* rules.

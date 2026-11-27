#!/usr/bin/env bash
# Verifies the obfuscated release jar: the Fabric entry point and all mixin
# classes must survive with their original names, while internal classes must
# be renamed. Exits non-zero (failing CI) if obfuscation didn't run or if it
# damaged something the mod needs to load.
set -euo pipefail

JAR="${1:?usage: verify-obfuscation.sh <release.jar>}"
[ -f "$JAR" ] || { echo "::error::release jar not found: $JAR"; exit 1; }

echo "=============================================================="
echo " Verifying $JAR"
echo "=============================================================="

LIST="$(unzip -Z1 "$JAR")"
fail=0
pass() { echo "  PASS  $1"; }
bad()  { echo "::error::$1"; fail=1; }

# 1. Fabric entry point must keep its exact name (fabric.mod.json points at it).
if grep -qx 'dev/loot/client/LootClient.class' <<<"$LIST"; then
  pass "entry point dev/loot/client/LootClient.class present"
else
  bad "entry point dev/loot/client/LootClient.class is missing or renamed"
fi

# 2. Every mixin listed in lootclient.mixins.json must keep its name.
MIXINS="CameraMixin ClientConnectionMixin ClientPlayNetworkHandlerMixin \
ClientPlayerInteractionManagerMixin ConnectScreenMixin EntityMixin \
EntityRendererMixin FogRendererMixin GameRendererAccessor GameRendererMixin \
HandledScreenAccessor InGameHudMixin KeyboardInputMixin LivingEntityMixin \
MinecraftClientAccessor ParticleManagerMixin PlayerListEntryMixin \
TextVisitFactoryMixin WorldMixin"
missing_mixins=""
for m in $MIXINS; do
  grep -qx "dev/loot/client/mixin/${m}.class" <<<"$LIST" || missing_mixins="$missing_mixins $m"
done
if [ -z "$missing_mixins" ]; then
  pass "all 19 mixin classes present with original names"
else
  bad "mixin classes missing/renamed:$missing_mixins"
fi

# 3. Mod metadata and mixin refmap must be present and intact.
for res in fabric.mod.json lootclient.mixins.json; do
  if grep -qx "$res" <<<"$LIST"; then pass "resource $res present"; else bad "resource $res missing"; fi
done
if grep -q 'refmap' <<<"$LIST"; then pass "mixin refmap present"; else
  echo "  WARN  no refmap entry found (ok only if mixins reference no remapped members)"; fi

# 4. Internal classes must be obfuscated: these names should be GONE.
SECRETS="module/combat/AutoCrystal module/combat/AimAssist module/render/Tracers \
util/Rotations util/CrystalUtil setting/NumberSetting"
leaked=""
for c in $SECRETS; do
  grep -qx "dev/loot/client/${c}.class" <<<"$LIST" && leaked="$leaked $c"
done
if [ -z "$leaked" ]; then
  pass "internal class names renamed (combat/render/util no longer plaintext)"
else
  bad "internal classes NOT obfuscated (names leaked):$leaked"
fi

# 5. Sanity: the entry point still declares onInitializeClient after obfuscation.
TMP="$(mktemp -d)"; unzip -q -o "$JAR" 'dev/loot/client/LootClient.class' -d "$TMP"
if javap -p -classpath "$TMP" dev.loot.client.LootClient | grep -q 'onInitializeClient'; then
  pass "LootClient.onInitializeClient() preserved"
else
  bad "LootClient.onInitializeClient() missing after obfuscation"
fi

rm -rf "$TMP"

echo
echo "---- evidence: sample of the original -> obfuscated name mapping ----"
MAP=""
for cand in "$(dirname "$JAR")/mapping.txt" "build/release/mapping.txt"; do
  [ -f "$cand" ] && { MAP="$cand"; break; }
done
if [ -n "$MAP" ]; then
  echo "  (from $MAP)"
  grep -E '^dev\.loot\.client\.(module|util|setting|gui|render)\.' "$MAP" | head -10 || true
else
  echo "  (mapping.txt not found next to jar - skipping sample)"
fi

echo
echo "---- class-name readability (fewer readable = more obfuscated) ----"
# grep returns 1 when it finds nothing, which is the GOOD case here, so guard
# every count against set -e / pipefail.
total=$( { grep -c '^dev/loot/client/.*\.class$' <<<"$LIST" || true; } )
readable=$( { grep -E '^dev/loot/client/.*/[A-Z][A-Za-z0-9]+\.class$' <<<"$LIST" \
             | grep -v '/mixin/' | grep -vc 'LootClient.class' || true; } )
echo "  total client classes: ${total:-0} ; still-readable non-mixin CamelCase names: ${readable:-0}"
echo "  (kept on purpose: LootClient + the 19 mixin classes)"

echo "=============================================================="
if [ "$fail" -ne 0 ]; then
  echo " RESULT: FAILED"
  exit 1
fi
echo " RESULT: OK - entry point + mixins intact, internals obfuscated"
echo "=============================================================="

"""Stage 9 tests: real Java 17 geometry/progression/wind, static source contracts.
Never interpret these as a full Fabric build or runtime test.
"""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
java = root / "src/main/java/tw/majin"
blast = (java / "ExplosionEffects.java").read_text()
render = (java / "client/BlastSphereRenderer.java").read_text()
sync = (java / "network/BlastSyncPayload.java").read_text()
config = (java / "AbilityConfig.java").read_text()
assert any("mod_version=" + v in (root / "gradle.properties").read_text() for v in ("0.9.0", "0.10.0"))
assert "double completedRadius()" in blast
assert "carver.completedRadius()" in blast
assert "BlastProgress.advanceToward(age, elapsedTicks, radius," in blast
assert "hitVolume(caster)" in blast
assert "BlastGeometry.overlapsBox(" in blast
assert "lastDamageTick.put(entity.getUuid(), elapsedTicks)" in blast
assert "9.0F" in blast
assert "snapshot.terrainSynced()" in render
assert "snapshot.terrainSynced()" in sync
assert "buf.writeBoolean(snapshot.terrainSynced())" in sync
assert "ChargeWind.point(" in render
assert "final int streams = 88;" in render
assert "if (carver != null)" in blast
assert 'properties.setProperty("explosion_terrain_enabled", "false")' in config
assert 'PlayerLookup.around(world, center, radius + 96.0)' in blast
for f in java.rglob('*.java'):
    source = f.read_text(encoding='utf8')
    clean = re.sub(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"', '', source, flags=re.S)
    assert clean.count('{') == clean.count('}'), f
if not shutil.which('javac') or not shutil.which('java'):
    raise SystemExit('Java JDK 17+ required for real mathematical tests')
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(['javac', '--release', '17', '-d', tmp,
                    str(java/'visual/BlastGeometry.java'),
                    str(java/'visual/BlastProgress.java'),
                    str(java/'visual/ChargeField.java'),
                    str(java/'visual/ChargeWind.java'),
                    str(root/'tests/BlastProgressTest.java'),
                    str(root/'tests/BlastVolumeDamageTest.java'),
                    str(root/'tests/ChargeWindTest.java')], check=True)
    for cls in ('BlastProgressTest', 'BlastVolumeDamageTest', 'ChargeWindTest'):
        subprocess.run(['java', '-cp', tmp, cls], check=True)
print('PASS: stage9 source contracts and Java progress/volume/wind math tests')
print('NOT RUN: full Fabric Gradle compilation, world edit integrations, multiplayer visual test')

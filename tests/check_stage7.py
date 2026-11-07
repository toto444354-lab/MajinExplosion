"""Stage 7: static source contracts + actual Java 17-compatible timeline tests.

This is not a full Fabric compile or an in-game integration test.
"""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
java = root / 'src/main/java/tw/majin'
config = (java / 'AbilityConfig.java').read_text(encoding='utf-8')
controller = (java / 'AbilityController.java').read_text(encoding='utf-8')
mod = (java / 'MajinExplosionMod.java').read_text(encoding='utf-8')
client = (java / 'client/MajinExplosionClient.java').read_text(encoding='utf-8')
renderer = (java / 'client/BlastSphereRenderer.java').read_text(encoding='utf-8')
props = (root / 'gradle.properties').read_text(encoding='utf-8')
assert any('mod_version=' + v in props for v in ('0.7.0', '0.8.0', '0.9.0', '0.10.0'))
assert 'properties.setProperty("terrain_absorption_enabled", "false")' in config
assert 'properties.setProperty("explosion_terrain_enabled", "false")' in config
assert 'properties.getProperty("explosion_terrain_enabled", "false")' in config
assert 'properties.getProperty("terrain_absorption_enabled", "false")' in config
assert 'public static void shutdown(MinecraftServer server)' in controller and 'STATES.clear()' in controller
assert 'AbilityController.shutdown(server);' in mod and 'ExplosionEffects.shutdown(server);' in mod
assert 'wasCharging = false;' in client and 'client.getNetworkHandler() == null' in client
assert 'entity.dropItem = false' not in (java / 'ChargeEffects.java').read_text(encoding='utf-8')
assert 'BlastTimeline.reconcileAge(age, incoming.age())' in renderer
assert 'BlastTimeline.advanceAge(blast.age)' in renderer
assert 'existing.update(snapshot);' in renderer
for file in java.rglob('*.java'):
    content = file.read_text(encoding='utf-8')
    cleaned = re.sub(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"', '', content, flags=re.S)
    assert cleaned.count('{') == cleaned.count('}'), file
if not shutil.which('javac') or not shutil.which('java'):
    raise SystemExit('Requires Java JDK 17+ to compile the pure-Java timeline test')
with tempfile.TemporaryDirectory() as directory:
    subprocess.run(['javac', '--release', '17', '-d', directory,
                    str(java / 'visual/BlastGeometry.java'),
                    str(java / 'visual/BlastTimeline.java'),
                    str(root / 'tests/BlastTimelineTest.java')], check=True)
    subprocess.run(['java', '-cp', directory, 'BlastTimelineTest'], check=True)
print('PASS: stage7 default safety, lifecycle, network timeline source checks')
print('NOT RUN: complete Minecraft/Fabric build or in-game checks')

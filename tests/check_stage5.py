"""Stage 5 offline assertions + javac-compiled real geometry tests (not Fabric build)."""
from pathlib import Path
import json
import re
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
java = root / 'src/main/java/tw/majin'
blast = (java / 'ExplosionEffects.java').read_text(encoding='utf-8')
client = (java / 'client/MajinExplosionClient.java').read_text(encoding='utf-8')
renderer = (java / 'client/BlastSphereRenderer.java').read_text(encoding='utf-8')
snapshot = (java / 'network/BlastSyncPayload.java').read_text(encoding='utf-8')
geometry = (java / 'visual/BlastGeometry.java').read_text(encoding='utf-8')
metadata = json.loads((root / 'src/main/resources/fabric.mod.json').read_text())
assert metadata['id'] == 'majin_explosion'
assert any('mod_version=' + v in (root / 'gradle.properties').read_text() for v in ('0.5.0', '0.6.0', '0.7.0', '0.8.0', '0.9.0'))
assert "version '1.6.12'" in (root / 'build.gradle').read_text()
assert 'BlastSphereRenderer.register()' in client
assert 'ClientPlayNetworking.registerGlobalReceiver(BlastSyncPayload.ID' in client
assert 'client.execute(() -> BlastSphereRenderer.accept(snapshot))' in client
assert 'BlastSyncPayload.write(buf,' in blast
assert 'if (age % 20 == 0) broadcastSnapshot();' in blast
assert 'ExplosionEffects.syncToPlayer(handler.player)' in (java / 'MajinExplosionMod.java').read_text()
assert 'ServerPlayNetworking.canSend(viewer, BlastSyncPayload.ID)' in blast
assert 'PlayerLookup.around(world, center, radius + 96.0)' in blast
assert 'WorldRenderEvents.LAST.register' in renderer
assert 'VertexFormats.POSITION_COLOR' in renderer
assert 'RenderSystem.depthMask(false)' in renderer
assert 'RenderSystem.depthMask(true)' in renderer
assert 'RenderSystem.defaultBlendFunc()' in renderer
assert 'BlastGeometry.counterclockwisePhase' in renderer
assert 'Math.sin(5.0 * (angle - phase)' in renderer
assert 'Math.max(0.0, Math.min(1.0, ageTicks / DURATION_TICKS))' in geometry
assert 'throw new IllegalArgumentException("Invalid blast snapshot")' in snapshot
for file in java.rglob('*.java'):
    content = file.read_text(encoding='utf-8')
    scrubbed = re.sub(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"', '', content, flags=re.S)
    assert scrubbed.count('{') == scrubbed.count('}'), file

if not shutil.which('javac') or not shutil.which('java'):
    raise SystemExit('Requires JDK 17+ for real geometry compilation')
with tempfile.TemporaryDirectory() as temp:
    subprocess.run(['javac', '--release', '17', '-d', temp,
                    str(java / 'visual/BlastGeometry.java'),
                    str(root / 'tests/BlastGeometryTest.java')], check=True)
    subprocess.run(['java', '-cp', temp, 'BlastGeometryTest'], check=True)
print('PASS: stage5 metadata, networking/render source-contract, brace balance')
print('NOT RUN: full Fabric/Minecraft Java build, Minecraft visual/runtime testing')

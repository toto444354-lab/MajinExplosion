"""Stage 6: source checks and actual javac/test for pure visual timing math.

This is NOT a full Fabric compile and NOT a Minecraft in-game rendering test.
"""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
java = root / 'src/main/java/tw/majin'
renderer = (java / 'client/BlastSphereRenderer.java').read_text(encoding='utf-8')
blast = (java / 'ExplosionEffects.java').read_text(encoding='utf-8')
profile = (java / 'visual/BlastVisualProfile.java').read_text(encoding='utf-8')
props = (root / 'gradle.properties').read_text(encoding='utf-8')
workflow = (root / '.github/workflows/build.yml').read_text(encoding='utf-8')

assert any('mod_version=' + v in props for v in ('0.6.0', '0.7.0', '0.8.0'))
assert 'BlastVisualProfile.flashAlpha(age)' in renderer
assert 'BlastVisualProfile.flashRadius(blast.snapshot.radius(), age)' in renderer
assert 'BlastVisualProfile.shockRadius(blast.snapshot.radius(), age)' in renderer
assert 'BlastVisualProfile.shockWidth(shockRadius)' in renderer
assert 'BlastVisualProfile.stripeAlpha(baseAlpha, a0, phase)' in renderer
assert 'for (int plane = 0; plane < 3; plane++)' in renderer
assert 'RenderSystem.defaultBlendFunc()' in renderer
assert 'world.playSound(null' in blast or 'blast.world.playSound(null' in blast
assert 'check_stage6.py' in workflow
assert 'flashAlpha' in profile and 'shockAlpha' in profile
for file in java.rglob('*.java'):
    source = file.read_text(encoding='utf-8')
    scrubbed = re.sub(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"', '', source, flags=re.S)
    assert scrubbed.count('{') == scrubbed.count('}'), file

if not shutil.which('javac') or not shutil.which('java'):
    raise SystemExit('Requires JDK 17+ for real visual profile tests')
with tempfile.TemporaryDirectory() as folder:
    subprocess.run(['javac', '--release', '17', '-d', folder,
                    str(java / 'visual/BlastGeometry.java'),
                    str(java / 'visual/BlastVisualProfile.java'),
                    str(root / 'tests/BlastVisualProfileTest.java')], check=True)
    subprocess.run(['java', '-cp', folder, 'BlastVisualProfileTest'], check=True)
print('PASS: stage6 metadata, rendering/sound source contracts and brace balance')
print('NOT RUN: full Fabric compile, multiplayer and in-game rendering tests')

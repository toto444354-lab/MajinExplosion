"""Stage 13: Java 17 geometry tests + static safety assertions; NOT a Fabric build."""
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
java = root/'src/main/java/tw/majin'
assert 'mod_version=0.13.0' in (root/'gradle.properties').read_text()
charge = (java/'ChargeEffects.java').read_text()
renderer = (java/'client/BlastSphereRenderer.java').read_text()
blast = (java/'ExplosionEffects.java').read_text()
config = (java/'AbilityConfig.java').read_text()
ability = (java/'AbilityController.java').read_text()
dummy = (java/'DummyController.java').read_text()
assert 'ParticleTypes.CAMPFIRE_COSY_SMOKE' in charge
assert 'ParticleTypes.CAMPFIRE_COSY_SMOKE' in blast
assert 'ParticleTypes.LARGE_SMOKE' not in charge[:charge.index('public static void pullMobs')]
assert 'ChargeWind.outwardPoint(' in renderer
assert 'windRibbons(transform, age, progress, phase);' in renderer
assert 'final int streams = 56' in renderer
assert 'ChargeEffects.pullMobs(' not in ability
assert 'ChargeEffects.pullMobs(' not in dummy
assert 'chargingSmokeIntervalTicks' in ability and 'chargingSmokeParticlesPerBurst' in dummy
assert '(age & 3) != 0' in blast and 'Math.min(6, particlesPerTick)' in blast
assert 'isChunkLoaded(' in blast
assert 'charging_smoke_interval_ticks' in config
assert 'charging_smoke_particles_per_burst' in config
assert 'residual_smoke_enabled' in config
for p in java.rglob('*.java'):
    source = p.read_text(encoding='utf8')
    clean = re.sub(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"', '', source, flags=re.S)
    assert clean.count('{') == clean.count('}'), f'unbalanced braces {p}'
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(['javac', '--release', '17', '-d', tmp,
        str(java/'visual/ChargeField.java'),
        str(java/'visual/ChargeWind.java'),
        str(java/'visual/ChargeDustPattern.java'),
        str(root/'tests/ChargeOutwardWindTest.java'),
        str(root/'tests/ChargeDustPatternTest.java')], check=True)
    subprocess.run(['java', '-cp', tmp, 'ChargeOutwardWindTest'], check=True)
    subprocess.run(['java', '-cp', tmp, 'ChargeDustPatternTest'], check=True)
print('PASS: stage13 campfire smoke, performance limits and outbound wind source checks')
print('NOT RUN: full Fabric/Gradle compile or in-game visual quality/performance test')

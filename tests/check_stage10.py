"""Stage 10: real standalone Java geometry / damage tests + source contracts.
This does not replace a Fabric compile nor Minecraft runtime test.
"""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
java = root / 'src/main/java/tw/majin'
blast = (java / 'ExplosionEffects.java').read_text(encoding='utf8')
config = (java / 'AbilityConfig.java').read_text(encoding='utf8')
assert 'mod_version=0.10.0' in (root / 'gradle.properties').read_text()
assert 'lastDamageTick.put(entity.getUuid(), elapsedTicks)' in blast
assert 'new BlockStateParticleEffect(ParticleTypes.BLOCK, material)' in blast
assert 'sprayTerrainRim(config)' in blast
assert 'world.isChunkLoaded(bx >> 4, bz >> 4)' in blast
assert 'config.explosionEvaporatesWater' in blast
assert 'config.residualSmokeEnabled' in blast
assert 'config.creativeCasterDeathEnabled' in blast
assert 'explosion_rim_debris_enabled' in config
assert 'rim_debris_particles_per_burst' in config
assert 'rim_smoke_particles_per_burst' in config
assert 'BlastDamageGate.canHit(elapsedTicks, lastTick)' in blast
assert 'BlastGeometry.overlapsBox(' in blast
assert '9.0F' in blast
assert 'BlastCrater.arrivalSquared(' in blast
assert 'BlastCrater.lowestY(' in blast and 'BlastCrater.highestY(' in blast
assert 'world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z)' in blast
assert 'BlastProgress.advanceToward(age, elapsedTicks, radius' in blast
assert 'MAX_BLOCK_SCANS_PER_JOB_TICK = 32768' in blast
assert 'MAX_COLUMNS_PER_JOB_TICK = 4096' in blast
assert 'MAX_SIMULTANEOUS_JOBS = 2' in blast
assert 'max_explosion_block_removals_per_tick", "4096"' in config
assert 'boundedInt(properties, "max_explosion_block_removals_per_tick", 4096, 64, 8192)' in config
assert 'if (!world.isChunkLoaded(x >> 4, z >> 4))' in blast
assert 'world.canPlayerModifyAt(owner, pos)' in blast
assert 'Block.NOTIFY_LISTENERS | Block.SKIP_DROPS' in blast
for p in java.rglob('*.java'):
    source = p.read_text(encoding='utf8')
    clean = re.sub(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"', '', source, flags=re.S)
    assert clean.count('{') == clean.count('}'), p
if not shutil.which('javac') or not shutil.which('java'):
    raise SystemExit('JDK 17 is required for stage10 Java tests')
with tempfile.TemporaryDirectory() as tmp:
    sources = [str(java/'visual'/name) for name in
               ('BlastGeometry.java', 'BlastCrater.java', 'BlastDamageGate.java', 'BlastProgress.java',
                'BlastRimSpray.java', 'CasterDeathRule.java', 'ResidualSmokePattern.java')]
    sources += [str(root/'tests'/name) for name in ('BlastCraterTest.java', 'BlastDamageGateTest.java',
                'BlastRimSprayTest.java', 'Stage10ExtraTest.java')]
    subprocess.run(['javac', '--release', '17', '-d', tmp, *sources], check=True)
    for name in ('BlastCraterTest', 'BlastDamageGateTest', 'BlastRimSprayTest', 'Stage10ExtraTest'):
        subprocess.run(['java', '-cp', tmp, name], check=True)
print('PASS: stage10 source checks, geometry, per-second damage, wind/rocks, smoke, and creative death tests')
print('NOT RUN: complete Fabric Gradle compile or Minecraft in-game explosion test')

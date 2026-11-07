"""Stage 11: independent Java regression + source-safety checks.

Not a Minecraft/Fabric Gradle compile. Run GitHub Actions for integration compile.
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
queue = (java / 'visual/RadiusBucketQueue.java').read_text(encoding='utf8')
version = (root / 'gradle.properties').read_text(encoding='utf8')
assert 'mod_version=0.11.0' in version
assert 'PriorityQueue<' not in blast, 'Expensive per-voxel priority heap still exists'
assert 'RadiusBucketQueue<Object>' in blast
assert 'ChunkSection section =' in blast and 'cursor.skipEmptySection()' in blast
assert 'microsBudget' in blast and 'System.nanoTime()' in blast
assert 'MAX_BLOCK_ENTITY_REMOVALS_PER_JOB_TICK' in blast
assert 'if (!state.isAir() && state.getHardness(world, position) >= 0.0F' in blast
assert '!state.hasBlockEntity()' not in blast, 'Functional blocks must not be categorically skipped'
assert 'BlockEntity tile = world.getBlockEntity(position)' in blast
assert 'if (tile instanceof Inventory inventory) inventory.clear();' in blast
assert 'Block.NOTIFY_LISTENERS | Block.SKIP_DROPS' in blast
assert 'world.canPlayerModifyAt(owner, position)' in blast
assert 'explosion_carve_micros_per_tick' in config
assert 'max_explosion_terrain_radius' in config
assert 'properties.setProperty("max_explosion_terrain_radius", "96")' in config
assert 'private final ArrayDeque<T>[] buckets;' in queue
for p in java.rglob('*.java'):
    source = p.read_text(encoding='utf8')
    clean = re.sub(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"', '', source, flags=re.S)
    assert clean.count('{') == clean.count('}'), p
if not shutil.which('javac') or not shutil.which('java'):
    raise SystemExit('JDK 17-compatible javac and java are needed for stage11 tests')
with tempfile.TemporaryDirectory() as tmp:
    sources = [str(java/'visual'/name) for name in (
        'RadiusBucketQueue.java', 'BlastGeometry.java', 'BlastCrater.java',
        'BlastDamageGate.java', 'BlastProgress.java', 'BlastRimSpray.java',
        'CasterDeathRule.java', 'ResidualSmokePattern.java')]
    tests = ['RadiusBucketQueueTest.java', 'BlastCraterTest.java',
        'BlastDamageGateTest.java', 'BlastRimSprayTest.java', 'Stage10ExtraTest.java']
    subprocess.run(['javac', '--release', '17', '-d', tmp, *sources,
                    *(str(root/'tests'/n) for n in tests)], check=True)
    for name in ('RadiusBucketQueueTest', 'BlastCraterTest',
                 'BlastDamageGateTest', 'BlastRimSprayTest', 'Stage10ExtraTest'):
        subprocess.run(['java', '-cp', tmp, name], check=True)
print('PASS: stage11 queue, geometry, damage, and source checks')
print('NOT RUN: Fabric/Gradle build or in-game block-entity and performance test')

"""Source-contract and pure-model tests; not a Java compile or Minecraft test."""
from pathlib import Path
import heapq
import json
import math
import re

ROOT = Path(__file__).resolve().parent.parent
java = ROOT / 'src/main/java/tw/majin'
controller = (java / 'AbilityController.java').read_text(encoding='utf-8')
blast = (java / 'ExplosionEffects.java').read_text(encoding='utf-8')
config = (java / 'AbilityConfig.java').read_text(encoding='utf-8')
modsrc = (java / 'MajinExplosionMod.java').read_text(encoding='utf-8')
props = (ROOT / 'gradle.properties').read_text(encoding='utf-8')
meta = json.loads((ROOT / 'src/main/resources/fabric.mod.json').read_text(encoding='utf-8'))
assert any('mod_version=' + v in props for v in ('0.4.0', '0.5.0', '0.6.0', '0.7.0', '0.8.0', '0.9.0', '0.10.0'))
assert meta['id'] == 'majin_explosion'
assert meta['depends']['minecraft'] == '1.20.1'
assert 'ExplosionEffects.start(player, state.ticks, config)' in controller
assert 'ExplosionEffects.tick(server, config)' in controller
assert 'MAX_RADIUS = BlastGeometry.MAX_RADIUS' in blast
assert 'VISUAL_TICKS = BlastGeometry.DURATION_TICKS' in blast
assert 'BlastGeometry.counterclockwisePhase(elapsedTicks, charge)' in blast, 'Counterclockwise phase must decrease'
assert 'new DustParticleEffect' in blast and 'ParticleTypes.END_ROD' in blast
assert 'caster.requestTeleport(anchor.x, anchor.y, anchor.z)' in blast
assert 'caster.setVelocity(Vec3d.ZERO)' in blast
assert 'caster.setNoGravity(true)' in blast
assert 'caster.setInvulnerable(true)' in blast
assert 'player.getDamageSources().genericKill()' in blast
assert 'world.getDamageSources().explosion' in blast
assert 'Block.NOTIFY_LISTENERS | Block.SKIP_DROPS' in blast
assert 'world.isChunkLoaded' in blast
assert 'state.getHardness(world, pos) >= 0.0F' in blast
assert 'state.hasBlockEntity()' in blast
assert 'world.canPlayerModifyAt(owner, pos)' in blast
assert 'MAX_BLOCK_SCANS_PER_JOB_TICK = 32768' in blast
assert 'MAX_COLUMNS_PER_JOB_TICK = 4096' in blast
assert 'removals < maxRemovals' in blast
assert 'MAX_SIMULTANEOUS_JOBS = 2' in blast
assert 'ExplosionEffects.onDisconnect(handler.player)' in modsrc
assert 'SERVER_STOPPING' in modsrc
for key in ('explosion_terrain_enabled', 'explosion_damage_enabled',
            'max_explosion_block_removals_per_tick'):
    assert key in config

# The actual row-frontier algorithm, mirrored as a pure numeric check.
# For a small test radius, verify complete circle coverage, no duplicates,
# and strictly nondecreasing squared-distance priority.
def visit_columns(r):
    heap = []
    for dz in range(-r, r + 1):
        max_x = math.isqrt(r*r - dz*dz)
        heapq.heappush(heap, (dz*dz, 0, dz, 1, max_x))
        if max_x >= 1:
            heapq.heappush(heap, (1 + dz*dz, -1, dz, -1, max_x))
    while heap:
        d2, dx, dz, direction, max_x = heapq.heappop(heap)
        yield dx, dz, d2
        nx = dx + direction
        if abs(nx) <= max_x:
            heapq.heappush(heap, (nx*nx + dz*dz, nx, dz, direction, max_x))

for r in (2, 7, 30, 60):
    actual = list(visit_columns(r))
    unique = {(x, z) for x, z, _ in actual}
    expected = {(x,z) for x in range(-r, r+1) for z in range(-r, r+1)
                if x*x + z*z <= r*r}
    assert len(actual) == len(unique) == len(expected) and unique == expected, r
    assert all(a[2] <= b[2] for a,b in zip(actual, actual[1:])), r
    # Center depth is greatest; edge depth reduces smoothly.
    depths = [56 * math.sqrt(max(0, 1 - d2 / (r * r))) for _,_,d2 in actual]
    assert all(a >= b - 1e-8 for a,b in zip(depths, depths[1:])), r

# Small geometry invariant: no scan ever crosses configured world height.
for r in (2, 7, 30):
    for _,_,d2 in visit_columns(r):
        profile = math.sqrt(max(0.0, 1.0 - d2/(r*r)))
        lo = math.ceil(65.0 - 56*profile)
        hi = math.floor(65.0 + 35*profile)
        assert lo <= hi
        assert 9 <= lo <= 65 <= hi <= 100

# Basic source integrity check for accidentally truncated Java file edits.
for file in java.rglob('*.java'):
    source = file.read_text(encoding='utf-8')
    scrubbed = re.sub(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"', '', source, flags=re.S)
    assert scrubbed.count('{') == scrubbed.count('}'), file

print('PASS: metadata, explosion source contracts, complete radial coverage, crater geometry, Java brace balance')
print('NOT RUN: javac/Gradle build, Fabric dependency resolution, Minecraft integration and visual testing')

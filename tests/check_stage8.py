"""Stage 8: regression contracts + javac compiled charging rules.
Full Fabric compile and in-game test are separate.
"""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
base = root / 'src/main/java/tw/majin'
controller = (base / 'AbilityController.java').read_text(encoding='utf-8')
client = (base / 'client/MajinExplosionClient.java').read_text(encoding='utf-8')
mod = (base / 'MajinExplosionMod.java').read_text(encoding='utf-8')
effects = (base / 'ChargeEffects.java').read_text(encoding='utf-8')
config = (base / 'AbilityConfig.java').read_text(encoding='utf-8')
assert 'mod_version=0.8.0' in (root / 'gradle.properties').read_text(encoding='utf-8')
assert 'STATES.remove(player.getUuid())' in controller
assert 'state.restoreGravity(player)' in controller
assert 'ChargeEffects.discardAll(state.effects)' in controller
assert 'new ChargeState(player)' in controller
assert 'state.lockPosition(player)' in controller
assert 'state.ticks = ChargeRules.advance(state.ticks)' in controller
assert 'state.restoreGravity(player);' in controller and 'if (!ExplosionEffects.start(' in controller
assert 'ChargeRules.canRelease(state.ticks)' in controller
assert 'if (charging) {' in client
assert 'client.player.input.movementForward = 0.0F;' in client
assert 'client.player.input.jumping = false;' in client
assert 'client.player.setVelocity(Vec3d.ZERO);' in client
assert 'AbilityController.clear(handler.player)' in mod
assert 'AbilityController.shutdown(server)' in mod
assert 'double pull = 0.20 + 0.28 * progress' in effects
assert 'properties.setProperty("terrain_absorption_enabled", "false")' in config
assert 'properties.setProperty("explosion_terrain_enabled", "false")' in config
assert 'ChargeField.touchesShell' in effects
assert 'mob.damage(world.getDamageSources().magic(), 1.0F)' in effects
assert 'ageTicks - previous >= 20' in effects
assert '9.0F' in (base / 'ExplosionEffects.java').read_text(encoding='utf-8')
assert 'ClientPlayNetworking.registerGlobalReceiver(ChargeSyncPayload.ID' in client
assert 'client.execute(() -> BlastSphereRenderer.acceptCharge(snapshot))' in client
renderer = (base / 'client/BlastSphereRenderer.java').read_text(encoding='utf-8')
assert 'chargeShell(transform' in renderer
assert 'windRibbons(transform' in renderer
assert 'ChargeField.clockwisePhase' in renderer
assert 'ParticleTypes.FLAME' not in controller + effects
assert 'ChargeSyncPayload.write(buffer' in controller
assert 'broadcastCharge(' in controller
assert 'new Identifier(MajinExplosionMod.MOD_ID, "charge_sync")' in (base / 'network/ChargeSyncPayload.java').read_text(encoding='utf-8')
for file in base.rglob('*.java'):
    src = file.read_text(encoding='utf-8')
    cleaned = re.sub(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"', '', src, flags=re.S)
    assert cleaned.count('{') == cleaned.count('}'), file
if not shutil.which('javac') or not shutil.which('java'):
    raise SystemExit('Requires Java JDK 17+ for charge rules test')
with tempfile.TemporaryDirectory() as path:
    subprocess.run(['javac', '--release', '17', '-d', path,
                    str(base / 'visual/ChargeRules.java'),
                    str(root / 'tests/ChargeRulesTest.java'),
                    str(base / 'visual/ChargeField.java'),
                    str(root / 'tests/ChargeFieldTest.java')], check=True)
    subprocess.run(['java', '-cp', path, 'ChargeRulesTest'], check=True)
    subprocess.run(['java', '-cp', path, 'ChargeFieldTest'], check=True)
print('PASS: stage8 freeze/cancel, mesh, wind, 1HP-on-contact/9HP-blast source checks and Java geometry')
print('NOT RUN: full Fabric Gradle build, Minecraft runtime movement test')

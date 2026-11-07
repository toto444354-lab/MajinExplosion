"""Stage12 checks. Only dust math compiles directly; Fabric integration needs Actions."""
from pathlib import Path
import subprocess
import tempfile
import re

root = Path(__file__).resolve().parent.parent
j = root / 'src/main/java/tw/majin'
version = (root/'gradle.properties').read_text(encoding='utf8')
ability = (j/'AbilityController.java').read_text(encoding='utf8')
dummy = (j/'DummyController.java').read_text(encoding='utf8')
blast = (j/'ExplosionEffects.java').read_text(encoding='utf8')
charge = (j/'ChargeEffects.java').read_text(encoding='utf8')
renderer = (j/'client/BlastSphereRenderer.java').read_text(encoding='utf8')
mod = (j/'MajinExplosionMod.java').read_text(encoding='utf8')
assert 'mod_version=0.12.0' in version
assert 'ChargeEffects.pullMobs(' not in ability
assert 'ChargeEffects.liftBlocks(' not in ability
assert 'ChargeEffects.tickDebris(' not in ability
assert 'ChargeEffects.damageOnContact(' in ability
assert 'ChargeEffects.spawnChargingDust(' in ability
assert 'windRibbons(transform' not in renderer
assert 'mob.setVelocity(' not in charge[:charge.index('public static void pullMobs')], 'contact-only function should not pull'
assert 'CommandRegistrationCallback.EVENT.register' in dummy
assert 'source.hasPermissionLevel(2)' in dummy
for command in ('spawn','charge','cancel','release','remove','status'):
    assert 'CommandManager.literal("'+command+'")' in dummy
assert 'DUMMIES.remove(owner.getUuid())' in dummy
assert 'state.visualTicks++' in dummy
assert 'ChargeRules.advance(state.ticks)' in dummy
assert 'ExplosionEffects.startDummy(' in dummy
assert 'DummyController.register()' in mod
assert 'DummyController.syncToPlayer(' in mod
assert 'DummyController.shutdown()' in mod
assert 'startDummy(ServerPlayerEntity owner, ArmorStandEntity dummy' in blast
assert 'if (dummy != null)' in blast
assert 'if (!dummy.isRemoved()) dummy.discard()' in blast
assert 'BLASTS.size() >= MAX_SIMULTANEOUS_JOBS' in blast
assert 'lastDamageTick.put(entity.getUuid(), elapsedTicks)' in blast
assert 'RadiusBucketQueue<Object>' in blast
for path in j.rglob('*.java'):
    t=path.read_text(encoding='utf8')
    clean=re.sub(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"','',t,flags=re.S)
    assert clean.count('{')==clean.count('}'),f'unbalanced braces: {path}'
with tempfile.TemporaryDirectory() as output:
    subprocess.run(['javac','--release','17','-d',output,
                    str(j/'visual/ChargeDustPattern.java'),
                    str(root/'tests/ChargeDustPatternTest.java')],check=True)
    subprocess.run(['java','-cp',output,'ChargeDustPatternTest'],check=True)
print('PASS: stage12 dummy commands, no suction, collision damage, client render and cleanup static checks')
print('NOT RUN: full Minecraft/Fabric compilation or game testing')

"""Dependency-free smoke checks; NOT a Java compile or an in-game test.

Run: python tests/check_stage3.py
"""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent.parent
src = (ROOT / 'src/main/java/tw/majin')
controller = (src / 'AbilityController.java').read_text(encoding='utf-8')
effects = (src / 'ChargeEffects.java').read_text(encoding='utf-8')
config = (src / 'AbilityConfig.java').read_text(encoding='utf-8')
client = (src / 'client/MajinExplosionClient.java').read_text(encoding='utf-8')
mod = json.loads((ROOT / 'src/main/resources/fabric.mod.json').read_text(encoding='utf-8'))
gradle = (ROOT / 'gradle.properties').read_text(encoding='utf-8')

assert mod['id'] == 'majin_explosion'
assert mod['depends']['minecraft'] == '1.20.1'
assert 'minecraft_version=1.20.1' in gradle
assert any('mod_version=' + v in gradle for v in ('0.3.0', '0.4.0', '0.5.0', '0.6.0', '0.7.0', '0.8.0', '0.9.0'))
assert 'GLFW_KEY_G' in client and 'GLFW_KEY_H' in client
assert 'Action.STOP_CHARGE' in client
assert 'MAX_TICKS = 200' in (src / 'visual/ChargeRules.java').read_text(encoding='utf-8')
assert 'ChargeEffects.pullMobs(player, state.effects, progress,' in controller
assert 'mob.damage(world.getDamageSources().magic(), 1.0F)' in effects
assert '!(entity instanceof PlayerEntity)' in effects
assert 'FallingBlockEntity.spawnFromBlock' in effects
assert 'entity.setDestroyedOnLanding()' in effects and 'entity.dropItem = false' not in effects
assert 'world.isChunkLoaded' in effects
assert 'world.canPlayerModifyAt' in effects
assert 'state.hasBlockEntity()' in effects
assert 'MAX_GLOBAL_ACTIVE_DEBRIS = 128' in controller
assert 'MAX_DEBRIS_LIFETIME_TICKS = 100' in effects
assert 'terrain_absorption_enabled' in config
assert 'mob_damage_enabled' in config
assert 'state.blockSpawnRemainder += config.blocksPerSecond;' in controller
assert 'state.blockSpawnRemainder -= 20;' in controller
assert 'ChargeEffects.discardAll(state.effects)' in controller

# Pure arithmetic verification of the same token-bucket schedule.
for rate in range(21):
    rest = 0
    launches = 0
    for tick in range(200):
        rest += rate
        if rest >= 20:
            rest -= 20
            launches += 1
    assert launches == rate * 10, (rate, launches)

# Lightweight balance check, excluding strings/comments, to catch partial edits.
for f in src.rglob('*.java'):
    text = f.read_text(encoding='utf-8')
    text = re.sub(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"', '', text, flags=re.S)
    assert text.count('{') == text.count('}'), f

print('PASS: JSON, metadata, source feature contracts, bucket rate checks, and brace balance')
print('NOT RUN: Gradle compile, dependencies, Minecraft integration or in-game visual tests')

#!/usr/bin/env python3
"""Cross-platform project build checker. Requires Python 3, JDK 17+, Gradle 8.6.

This script is deliberately fail-closed: no JAR is reported unless Gradle
completes successfully and the expected remapped mod artifact exists.
"""
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
VERSION = '0.8.0'


def run(args):
    print('> ' + ' '.join(str(part) for part in args), flush=True)
    subprocess.run(args, cwd=ROOT, check=True)


def main():
    print('Majin Explosion build check (Fabric 1.20.1)', flush=True)
    if not shutil.which('javac'):
        sys.exit('ERROR: JDK 17+ (javac) is required')
    tests = [f'tests/check_stage{n}.py' for n in range(3, 9)]
    for test in tests:
        run([sys.executable, test])
    wrapper = ROOT / ('gradlew.bat' if sys.platform == 'win32' else 'gradlew')
    if wrapper.is_file():
        gradle = str(wrapper)
    else:
        gradle = shutil.which('gradle')
    if not gradle:
        sys.exit('ERROR: Gradle 8.6 not installed and this project has no Gradle wrapper. '
                 'Offline source/Java tests passed; Minecraft JAR NOT built.')
    run([gradle, '--no-daemon', 'clean', 'build'])
    jar = ROOT / 'build' / 'libs' / f'majin-explosion-{VERSION}.jar'
    if not jar.is_file() or jar.stat().st_size == 0:
        sys.exit(f'ERROR: Gradle finished but expected remapped JAR not found: {jar}')
    print(f'SUCCESS: remapped mod JAR: {jar} ({jar.stat().st_size:,} bytes)')
    print('NOTE: this confirms a build, not Minecraft runtime or multiplayer behavior.')


if __name__ == '__main__':
    main()

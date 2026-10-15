#!/usr/bin/env python3
"""Build the nullable-use correction for the published 3.4.0 short-read JVM artifact."""
import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile

BASE = 'a161b07fb1b459371458ae6d9508ec31df280428'
NULLABLE_FIX = 'fe6ac99a29ec207c9c5f4421f3fed62fd5bc9d45'
PREVIOUS_VERSION = '3.4.0-r576-short-read-1'
PREVIOUS_SHA256 = 'b0f2a8296acb1818a03f370ecea763e273224a04d6c0046c190ebb0a82a77061'
VERSION = '3.4.0.2'
ARTIFACT = 'okio-jvm'
REPOSITORY = 'https://kotlin.directory/com/squareup/okio/okio-jvm'


def command(*args):
    return subprocess.check_output(args, text=True, timeout=180).strip()


def archive(path, entries):
    with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_DEFLATED) as jar:
        for name, data in sorted(entries.items()):
            entry = zipfile.ZipInfo(name, (1980, 2, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o644 << 16
            jar.writestr(entry, data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-directory', required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    output = args.output_directory.resolve()
    if output.exists() and any(output.iterdir()):
        raise ValueError(f'Output directory must be absent or empty: {output}')
    output.mkdir(parents=True, exist_ok=True)
    cs = shutil.which('coursier') or shutil.which('cs')
    if cs is None:
        raise RuntimeError('Coursier is required on PATH to resolve the Kotlin compiler')
    compiler = command(cs, 'fetch', '-r', 'https://kotlin.directory',
                       'org.jetbrains.kotlin:kotlin-compiler-embeddable:1.8.22', '--classpath')
    dependencies = command(cs, 'fetch', '-r', 'https://kotlin.directory',
                           'org.jetbrains.kotlin:kotlin-stdlib:1.8.0', '--classpath')
    paths = command('git', '-C', str(root), 'ls-tree', '-r', '--name-only', BASE,
                    'okio/src/commonMain', 'okio/src/jvmMain').splitlines()
    paths = [path for path in paths if path.endswith('.kt')]
    with tempfile.TemporaryDirectory(prefix='nullable-use-', dir=output.parent) as temporary:
        temp = Path(temporary)
        baseline = temp / 'previous.jar'
        with urllib.request.urlopen(f'{REPOSITORY}/{PREVIOUS_VERSION}/{ARTIFACT}-{PREVIOUS_VERSION}.jar', timeout=60) as response:
            baseline.write_bytes(response.read())
        digest = hashlib.sha256(baseline.read_bytes()).hexdigest()
        if digest != PREVIOUS_SHA256:
            raise ValueError(f'Previous artifact SHA-256 was {digest}, expected {PREVIOUS_SHA256}')
        sources = []
        for path in paths:
            target = temp / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(subprocess.check_output(['git', '-C', str(root), 'show', f'{BASE}:{path}'], timeout=30))
            sources.append(target)
        common = [str(path) for path in sources if '/commonMain/' in str(path)]
        compiler_args = ['java', '-Xmx2g', '-cp', compiler, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                         '-no-stdlib', '-no-reflect', '-language-version', '1.8', '-api-version', '1.8',
                         '-jvm-target', '1.8', '-module-name', 'okio', '-Xjvm-default=all', '-Xmulti-platform',
                         '-Xcommon-sources=' + ','.join(common), '-cp', dependencies]
        original = temp / 'original.jar'
        fixed = temp / 'fixed.jar'
        subprocess.run(compiler_args + ['-d', str(original)] + [str(path) for path in sources], check=True, timeout=600)
        okio_source = temp / 'okio/src/commonMain/kotlin/okio/Okio.kt'
        okio_source.write_bytes(subprocess.check_output(['git', '-C', str(root), 'show',
                                                       f'{NULLABLE_FIX}:okio/src/commonMain/kotlin/okio/Okio.kt'], timeout=30))
        subprocess.run(compiler_args + ['-d', str(fixed)] + [str(path) for path in sources], check=True, timeout=600)
        with zipfile.ZipFile(original) as before, zipfile.ZipFile(fixed) as after, zipfile.ZipFile(baseline) as previous:
            if set(before.namelist()) != set(after.namelist()):
                raise ValueError('Nullable correction unexpectedly changed the compiled entry names')
            changed = {name: after.read(name) for name in after.namelist() if after.read(name) != before.read(name)}
            if not changed or any(not name.endswith('.class') for name in changed):
                raise ValueError(f'Expected only changed class bodies, found {sorted(changed)}')
            if not set(changed).issubset(previous.namelist()):
                raise ValueError('Nullable correction added classes absent from the previous artifact')
            entries = {name: previous.read(name) for name in previous.namelist()}
            entries.update(changed)
            entries['META-INF/MANIFEST.MF'] = entries['META-INF/MANIFEST.MF'].replace(
                f'Bundle-Version: 3.4.0.r576-short-read-1'.encode(), f'Bundle-Version: {VERSION}'.encode())
            archive(output / f'{ARTIFACT}-{VERSION}.jar', entries)
            print('Updated class bodies: ' + ', '.join(sorted(changed)))
            for name in ['okio/JvmFileHandle.class', 'okio/AsyncTimeout$Watchdog.class']:
                if entries[name] != previous.read(name):
                    raise ValueError(f'Unrelated class changed: {name}')
        with urllib.request.urlopen(f'{REPOSITORY}/{PREVIOUS_VERSION}/{ARTIFACT}-{PREVIOUS_VERSION}.pom', timeout=60) as response:
            pom = response.read().decode()
        if pom.count(f'<version>{PREVIOUS_VERSION}</version>') != 1:
            raise ValueError('Expected exactly one previous project version in POM')
        pom = pom.replace(f'<version>{PREVIOUS_VERSION}</version>', f'<version>{VERSION}</version>')
        (output / f'{ARTIFACT}-{VERSION}.pom').write_text(pom)
    for path in sorted(output.iterdir()):
        print(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}')


if __name__ == '__main__':
    main()

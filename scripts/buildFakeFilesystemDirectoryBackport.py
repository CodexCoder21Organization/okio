#!/usr/bin/env python3
"""Build the 3.4.0 FakeFileSystem directory correction without publishing it."""
import argparse
import hashlib
import re
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile

VERSION = '3.4.0-r576-registry-lock-2'
PREVIOUS = '3.4.0-r576-registry-lock-1'
PREVIOUS_SHA256 = '1f6dbb73ed4c0f32adcb914e77eaf40b9f4217990742554ebb1603681097e72c'
ARTIFACT = 'okio-fakefilesystem-jvm'
REPOSITORY = f'https://kotlin.directory/com/squareup/okio/{ARTIFACT}'


def command(*args):
    return subprocess.check_output(args, text=True, timeout=180).strip()


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
        raise RuntimeError('Coursier is required on PATH')
    compiler = command(cs, 'fetch', '-r', 'https://kotlin.directory',
                       'org.jetbrains.kotlin:kotlin-compiler-embeddable:1.8.0', '--classpath')
    dependencies = command(cs, 'fetch', '-r', 'https://kotlin.directory',
                           'com.squareup.okio:okio-jvm:3.4.0',
                           'org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0',
                           'org.jetbrains.kotlinx:atomicfu-jvm:0.20.2', '--classpath')
    sources = sorted((root / 'backports/fakefilesystem-3.4.0/src').glob('*.kt'))
    with tempfile.TemporaryDirectory(prefix='okio-directory-', dir=output.parent) as temporary:
        temp = Path(temporary)
        with urllib.request.urlopen(f'{REPOSITORY}/{PREVIOUS}/{ARTIFACT}-{PREVIOUS}.jar', timeout=60) as response:
            previous = response.read()
        actual = hashlib.sha256(previous).hexdigest()
        if actual != PREVIOUS_SHA256:
            raise ValueError(f'Previous artifact SHA-256 was {actual}, expected {PREVIOUS_SHA256}')
        jar = output / f'{ARTIFACT}-{VERSION}.jar'
        subprocess.run(['java', '-Xmx2g', '-cp', compiler, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                        '-no-stdlib', '-no-reflect', '-language-version', '1.8', '-api-version', '1.8',
                        '-jvm-target', '1.8', '-module-name', 'okio-fakefilesystem', '-cp', dependencies,
                        '-d', str(jar), *map(str, sources)], check=True, timeout=600)
        # The registry lock is compiled to JVM monitors and adds no runtime dependency.
        with zipfile.ZipFile(jar) as built:
            for name in built.namelist():
                if not name.endswith('.class'):
                    continue
                bytecode = command('javap', '-verbose', '-classpath', str(jar), name[:-6].replace('/', '.'))
                if re.search(r'= (?:Class|Methodref|Fieldref|InterfaceMethodref).*kotlinx/atomicfu', bytecode):
                    raise ValueError(f'Unexpected atomicfu runtime reference in {name}')
        (temp / 'previous.jar').write_bytes(previous)
        classes = command('jar', 'tf', str(temp / 'previous.jar')).splitlines()
        class_names = sorted(name for name in classes if name.endswith('.class'))
        with zipfile.ZipFile(jar) as built:
            if class_names != sorted(name for name in built.namelist() if name.endswith('.class')):
                raise ValueError('Compiled class names differ from the -1 artifact')
        public_classes = [name[:-6].replace('/', '.') for name in class_names]
        before = command('javap', '-public', '-classpath', str(temp / 'previous.jar'), *public_classes)
        after = command('javap', '-public', '-classpath', str(jar), *public_classes)
        if before != after:
            raise ValueError('Public javap API differs from the -1 artifact')
        print('Public javap API is identical to -1 for all previous classes.')
        with urllib.request.urlopen(f'{REPOSITORY}/{PREVIOUS}/{ARTIFACT}-{PREVIOUS}.pom', timeout=60) as response:
            pom = response.read().decode()
        if pom.count(f'<version>{PREVIOUS}</version>') != 1:
            raise ValueError('Expected one previous project version in the POM')
        pom = pom.replace(f'<version>{PREVIOUS}</version>', f'<version>{VERSION}</version>')
        (output / f'{ARTIFACT}-{VERSION}.pom').write_text(pom)
    for path in sorted(output.iterdir()):
        print(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}')


if __name__ == '__main__':
    main()

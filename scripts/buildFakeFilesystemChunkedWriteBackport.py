#!/usr/bin/env python3
"""Build the compatible 3.4.0 FakeFileSystem chunked-write correction locally."""
import argparse
import difflib
import hashlib
import re
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile

VERSION = '3.4.0.2'
PREVIOUS = '3.4.0.1'
PREVIOUS_SHA256 = 'ce679551d2812f30a71f3a883e84eeb0c282b470b37af722a452ac5a23909715'
ARTIFACT = 'okio-fakefilesystem-jvm'
REPOSITORY = f'https://kotlin.directory/com/squareup/okio/{ARTIFACT}'


def command(*args):
    return subprocess.check_output(args, text=True, timeout=180).strip()


def normalized_signatures(output):
    # Source filenames and method declaration order are not part of the JVM API.
    blocks = []
    members = []
    for line in output.splitlines():
        if line.startswith('Compiled from '):
            continue
        if line.startswith('  '):
            members.append(line)
        else:
            if line == '}':
                blocks.extend(sorted(members))
                members = []
            blocks.append(line)
    return '\n'.join(blocks)


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
    with tempfile.TemporaryDirectory(prefix='okio-chunked-write-', dir=output.parent) as temporary:
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
        # The backported source depends on Okio 3.4.0 and emits no atomicfu runtime references.
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
                raise ValueError('Compiled class names differ from the 3.4.0.1 artifact')
        # Nested Element.File is private storage, not exported JVM API.
        public_classes = [name[:-6].replace('/', '.') for name in class_names
                          if name != 'okio/fakefilesystem/FakeFileSystem$Element$File.class']
        before = normalized_signatures(command('javap', '-public', '-classpath', str(temp / 'previous.jar'), *public_classes))
        after = normalized_signatures(command('javap', '-public', '-classpath', str(jar), *public_classes))
        if before != after:
            raise ValueError('Public javap API differs from the 3.4.0.1 artifact:\n' +
                             '\n'.join(difflib.unified_diff(before.splitlines(), after.splitlines(),
                                                            fromfile='3.4.0.1', tofile=VERSION)))
        print('Exported javap API is identical to 3.4.0.1; private Element.File storage is excluded.')
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

#!/usr/bin/env python3
"""Build the 3.4.0 JVM filesystem artifact with the lookup allocation fix."""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

BASE = 'a9bbcaa30a3ce0c31881b2c83525f15d195a68a7'
VERSION = '3.4.0-te-cost.1'
ARTIFACT = 'okio-fakefilesystem-jvm'
CS = '/home/u/bin/cs'


def command(*args):
    return subprocess.check_output(args, text=True).strip()


def replace_once(source, before, after):
    if source.count(before) != 1:
        raise ValueError(f'Expected one source occurrence of {before!r}, found {source.count(before)}')
    return source.replace(before, after)


def archive(target, entries):
    with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED) as jar:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (1980, 2, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            jar.writestr(info, data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-directory', required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    os.chdir(root)
    os.environ['JAVA_OPTS'] = '-Xmx2g'
    output = args.output_directory.resolve()
    if output.exists() and any(output.iterdir()):
        raise ValueError(f'Output directory must be empty: {output}')
    output.mkdir(parents=True, exist_ok=True)
    sources = {}
    for name in ['FakeFileSystem.kt', 'FileMetadataCommon.kt']:
        sources[name] = command('git', 'show', f'{BASE}:okio-fakefilesystem/src/commonMain/kotlin/okio/fakefilesystem/{name}') + '\n'
    s = sources['FakeFileSystem.kt']
    replacements = [
        ('var currentPath: Path = rootPath', 'var currentPath: Path? = null'),
        ('throw IOException("not a directory: $currentPath")', 'throw IOException("not a directory: ${currentPath ?: pathPrefix(canonicalPath, segmentsTraversed)}")'),
        ('currentPath /= segment', 'currentPath = currentPath?.resolve(segment)'),
        ('currentPath = currentPath.parent!!.resolve(current.target, normalize = true)', 'currentPath = (currentPath ?: pathPrefix(canonicalPath, segmentsTraversed)).parent!!.resolve(current.target, normalize = true)'),
        ('PathLookupResult(currentPath, parent, lastSegment, current)', 'PathLookupResult(currentPath ?: canonicalPath, parent, lastSegment, current)'),
        ('PathLookupResult(currentPath, parent, lastSegment, null)', 'PathLookupResult(currentPath ?: canonicalPath.parent ?: pathPrefix(canonicalPath, segmentsTraversed), parent, lastSegment, null)'),
    ]
    for before, after in replacements:
        s = replace_once(s, before, after)
    s += '\nprivate fun pathPrefix(path: Path, count: Int): Path {\n  var result = path.root!!\n  for (segment in path.segmentsBytes.take(count)) result /= segment\n  return result\n}\n'
    sources['FakeFileSystem.kt'] = s
    compiler = command(CS, 'fetch', '-r', 'https://kotlin.directory', 'org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.25', '--classpath')
    dependencies = command(CS, 'fetch', '-r', 'https://kotlin.directory', 'com.squareup.okio:okio-jvm:3.4.0', 'org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0', 'org.jetbrains.kotlin:kotlin-stdlib:1.8.0', '--classpath')
    with tempfile.TemporaryDirectory(prefix='okio-lookup-backport-') as temporary:
        temp = Path(temporary)
        for name, text in sources.items():
            (temp / name).write_text(text)
        subprocess.run(['java', '-Xmx2g', '-cp', compiler, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-language-version', '1.8', '-api-version', '1.8', '-jvm-target', '1.8', '-module-name', 'okio-fakefilesystem', '-cp', dependencies, '-d', str(temp / 'module.jar'), *[str(temp / name) for name in sources]], check=True)
        with zipfile.ZipFile(temp / 'module.jar') as jar:
            archive(output / f'{ARTIFACT}-{VERSION}.jar', {name: jar.read(name) for name in jar.namelist() if not name.endswith('/')})
    archive(output / f'{ARTIFACT}-{VERSION}-sources.jar', {f'commonMain/okio/fakefilesystem/{name}': text.encode() for name, text in sources.items()})
    pom = f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.squareup.okio</groupId>
  <artifactId>{ARTIFACT}</artifactId>
  <version>{VERSION}</version>
  <name>okio-fakefilesystem</name>
  <description>Okio 3.4.0 FakeFileSystem with linear ordinary-path lookup allocation.</description>
  <url>https://github.com/CodexCoder21Organization/okio/pull/2</url>
  <licenses><license><name>The Apache Software License, Version 2.0</name><url>https://www.apache.org/licenses/LICENSE-2.0.txt</url><distribution>repo</distribution></license></licenses>
  <dependencies>
    <dependency><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-stdlib-jdk8</artifactId><version>1.8.0</version><scope>compile</scope></dependency>
    <dependency><groupId>org.jetbrains.kotlinx</groupId><artifactId>kotlinx-datetime-jvm</artifactId><version>0.4.0</version><scope>compile</scope></dependency>
    <dependency><groupId>com.squareup.okio</groupId><artifactId>okio-jvm</artifactId><version>3.4.0</version><scope>compile</scope></dependency>
    <dependency><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-stdlib-common</artifactId><version>1.8.0</version><scope>compile</scope></dependency>
  </dependencies>
</project>
'''
    (output / f'{ARTIFACT}-{VERSION}.pom').write_text(pom)
    for path in sorted(output.iterdir()):
        print(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}')


if __name__ == '__main__':
    main()

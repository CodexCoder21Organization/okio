Okio
====

See the [project website][okio] for documentation and APIs.

Okio is a library that complements `java.io` and `java.nio` to make it much
easier to access, store, and process your data. It started as a component of
[OkHttp][1], the capable HTTP client included in Android. It's well-exercised
and ready to solve new problems.

Compatible 3.4.0 JVM artifact
---------------------------

The fork builds `com.squareup.okio:okio-jvm:3.4.0.3` for
consumers retaining the 3.4.0 API. Version `3.4.0.3` retains the short-read and
nullable-result corrections from `3.4.0.2` and corrects concurrent NIO directory
creation. With `mustCreate = false`, another caller creating the same directory
is successful; a file at the target or any other creation error still fails.
With `mustCreate = true`, an existing directory still fails. The fourth numeric
segment sorts above `3.4.0` and below `3.5.0`, so ordinary dependency resolution
selects it over transitive `3.4.0` requests. The compatible build produces only
`okio-jvm`.

The retained nullable correction allows `use`, `FileSystem.read`, and
`FileSystem.write` blocks to return null. Resources still close when the block
returns or throws. Consumers must rebuild after updating that correction because
these functions are inline.

Build and test the JAR and POM with Java, Python 3, and Coursier on PATH:

```bash
git fetch origin
git rebase origin/main
python3 scripts/buildNioDirectoryCreationBackport.py --output-directory /tmp/okio-directory-creation
scripts/testNioDirectoryCreationBackport.bash /tmp/okio-directory-creation/okio-jvm-3.4.0.3.jar /tmp/okio-directory-results
```

The output directory must be absent or empty. The script starts with the
checksum-verified `3.4.0.2` artifact and applies the directory correction to the
3.4.0 source at `parent-3.4.0` (`a161b07fb1b459371458ae6d9508ec31df280428`).
It replaces only the NIO adapter class, preserving unrelated classes, the original
POM dependency versions, and the existing 3.4.0 error wording. These commands
build and test locally without publishing. The owning native scenarios are
`ConcurrentNioDirectoryCreationTest`, `ExistingNioDirectoryFailureTest`, and
`ReplacedNioDirectoryCreationTest`; run
the JVM suite with `./gradlew :okio:jvmTest`. The compatible runner executes those
same scenarios with a 128 MiB heap and the historical must-create message spelling.
The prior nullable build remains available in `scripts/buildNullableUseBackport.py`;
its native regression is `UseNullableResultTest`.

License
--------

    Copyright 2013 Square, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
    
 [1]: https://github.com/lysine-dev/okhttp
 [okio]: https://lysine.dev/okio/

Compatible 3.4.0 FakeFileSystem JVM artifact
------------------------------------------

`com.squareup.okio:okio-fakefilesystem-jvm:3.4.0-r576-registry-lock-4`
preserves directories and their children when file, directory, symlink or move
admission overlaps another caller or a re-entrant clock callback. Opens and
deletion and metadata updates resolve the current entry after callbacks; must-create and must-exist
checks use that current entry. Every primitive mutator resolves symlinks, validates
its target and applies its change in one critical section. Reads finish clock
callbacks before resolving the current path under that same lock. Dangling
links are errors where an existing target is required; metadata still describes
the link itself. `atomicMove` replaces the destination link itself and preserves
its referent and unrelated siblings. Inherited `createDirectories` uses current
directory metadata rather than entries from a retired symlink parent. Compound
methods such as `copy`, `deleteRecursively` and recursive `createDirectories` use
separate primitive transactions and are not atomic as a whole against independent
concurrent changes. `createDirectory(path, mustCreate = true)`
rejects an existing entry; ordinary directory creation leaves an existing
directory unchanged and rejects a file at the target path. The artifact retains
the open-file registry correction from `3.4.0-r576-registry-lock-1` and its public
JVM API and dependency versions. The compatible sources are in
`backports/fakefilesystem-3.4.0/src`; they start from upstream `parent-3.4.0`
plus the [previous registry correction](https://github.com/CodexCoder21Organization/BuildTestEmbedded/blob/main/docs/r576-fakefilesystem-concurrency.md).

This is the historical registry-lock-4 artifact and build. The source and build
script remain available for provenance, but the current full selected regression
suite below includes the bulk-read test and therefore requires version `3.4.0.1`.
That build also checks the prior artifact checksum and preserves the public API
and dependency versions. The directory concurrency tests continue to use an
in-memory filesystem, three repetitions of each directory and entry-admission
regression, and the unchanged r576 workload of 20 trials, nine callers and up to
10,000 operations per caller.

Compatible 3.4.0 FakeFileSystem bulk-read JVM artifact
----------------------------------------------------

`com.squareup.okio:okio-fakefilesystem-jvm:3.4.0.1` builds on the registry correction above with bulk copies for random-access `FileHandle.read` calls over segmented file contents. Offsets at or beyond the file size return EOF before narrowing, so a large `Long` offset cannot wrap back into the file. The artifact keeps the existing public JVM API and dependency versions.

The repeated read test performs 1,000 reads of 32 MiB in each of ten
repetitions. It compares current-thread CPU time with an equivalent
`ByteString.copyInto` bulk-copy control, with an 8x ceiling, and retains the
existing exact-byte, destination-range, and EOF assertions.

With Java, Python 3 and Coursier on PATH, build locally without publishing (the output directory must be absent or empty):

```bash
python3 scripts/buildFakeFilesystemBulkReadBackport.py --output-directory /tmp/okio-bulk-read-artifact
scripts/testFakeFilesystemDirectoryBackport.bash /tmp/okio-bulk-read-artifact /tmp/okio-bulk-read-results
scripts/testFakeFilesystemBulkReadBackport.bash /tmp/okio-bulk-read-artifact/okio-fakefilesystem-jvm-3.4.0.1.jar /tmp/okio-bulk-read-core-3.4.0-results
```

The directory runner executes the full selected native regression suite against core `okio-jvm:3.4.0.2`; the focused runner exercises the read behavior against consumer core `okio-jvm:3.4.0`.

Compatible 3.4.0 FakeFileSystem append JVM artifact
-------------------------------------------------

`com.squareup.okio:okio-fakefilesystem-jvm:3.4.0.2` retains the bulk-read and
registry corrections above and makes sequential appends amortized linear in the
bytes appended, including append sinks reopened between blocks. Old open file
handles retain their generation's bytes and length when a new writer is opened.
Writes and reads copy caller arrays; truncation followed by extension fills the
new range with zeroes. The end-user JVM API and dependency versions are unchanged.

Build and run the compatible regression suite locally (the artifact output
must be absent or empty):

```bash
python3 scripts/buildFakeFilesystemAppendBackport.py --output-directory /tmp/okio-append-artifact
scripts/testFakeFilesystemDirectoryBackport.bash /tmp/okio-append-artifact /tmp/okio-append-results
scripts/testFakeFilesystemAppendBackport.bash /tmp/okio-append-artifact/okio-fakefilesystem-jvm-3.4.0.2.jar /tmp/okio-append-core-results
```

The append regression counts bytes allocated by the current JVM thread while
writing 8 MiB and 32 MiB through public file handles and reopened append sinks.
Its verdict uses allocation growth and byte bounds, with exact-content and
resource-cleanup checks. It does not use a timing threshold. The focused runner
uses core Okio 3.4.0, matching consumers that keep that API version.

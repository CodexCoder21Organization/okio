Okio
====

See the [project website][okio] for documentation and APIs.

Okio is a library that complements `java.io` and `java.nio` to make it much
easier to access, store, and process your data. It started as a component of
[OkHttp][1], the capable HTTP client included in Android. It's well-exercised
and ready to solve new problems.

Compatible 3.4.0 JVM artifact
---------------------------

The fork publishes `com.squareup.okio:okio-jvm:3.4.0.2` for
consumers retaining the 3.4.0 API. Version `3.4.0.2` is upstream 3.4.0 plus
the short-read and nullable-result corrections. The fourth numeric segment
sorts above `3.4.0` and below `3.5.0`, so ordinary dependency resolution selects
it over transitive `3.4.0` requests. It contains the same corrected class bodies
as `3.4.0-r576-short-read-2`; the older hyphenated version sorts below the plain
release in the consumer build API. This compatible release publishes only
`okio-jvm`, which is the module produced by the backport build script. It preserves the short-read correction in
`3.4.0-r576-short-read-1` and allows `use`, `FileSystem.read`, and
`FileSystem.write` blocks to return null. Resources still close when the block
returns or throws. Consumers must rebuild after updating the dependency because
these functions are inline.

Build the JAR and POM with Java, Python 3, and Coursier on PATH:

```bash
git fetch origin
git rebase origin/main
python3 scripts/buildNullableUseBackport.py --output-directory /tmp/okio-nullable-use
```

The output directory must be absent or empty. The script starts with the
checksum-verified previous artifact and applies the nullable correction from
upstream commit `fe6ac99a29ec207c9c5f4421f3fed62fd5bc9d45` to the 3.4.0
source at `parent-3.4.0` (`a161b07fb1b459371458ae6d9508ec31df280428`).
It preserves all unrelated classes and the original POM dependency versions.
The command builds locally and does not publish. The owning tests are
`UseNullableResultTest` in the native Okio test suite (`./gradlew :okio:jvmTest`).

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

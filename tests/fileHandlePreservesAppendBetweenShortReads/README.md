# Short-read regression

The BuildTestEmbedded utilization work found that an overlapping URL spool drain could return
201 callbacks for 200 physical lines, or 200 callbacks with a line replaced. The mechanism is in
`JvmFileHandle.protectedRead`: after a short OS read, a producer can append before the next OS
read. Reusing `arrayOffset` overwrites the earlier bytes; the destination must advance by
`bytesRead`.

Run this one test using the lane's host-wide slot wrapper:

```
tb.sh /absolute/path/to/okio --test fileHandlePreservesAppendBetweenShortReads
```

The script compiles the checked-out `JvmFileHandle.kt` against published Okio 3.4.0, then makes a
local copy of that jar with only the compiled `JvmFileHandle.class` replaced. This tests the source
correction against the exact dependency version used by the consumer. It does not publish a jar,
modify dependency caches, or run the multiplatform suite. All generated files stay under ignored
`build/short-read/`.

`ShortReadRegression` adds a callback immediately after the real `RandomAccessFile.read` call.
The callback appends to the real file, without altering the byte array, read result, requested
length, or offsets. It forces two successive short-read/append boundaries and verifies public
`FileHandle` array reads with a nonzero destination offset, appending to an existing `Buffer`, and
buffered sources with a nonzero file offset. It also checks physical file contents, EOF, source
position, and exactly two callback executions. No test uses reflection or changes member visibility.

The same script also builds a local source-replacement agent for consumer validation. After the
source-layer test passes, run a consumer test through its usual wrapper with:

```
JAVA_TOOL_OPTIONS='-javaagent:/absolute/path/to/okio/build/short-read/replacement-agent.jar=/absolute/path/to/okio/build/short-read/classes/okio/JvmFileHandle.class' \
  tb.sh /absolute/path/to/BuildTestEmbedded --test urlProgressDrainPreservesAppendBetweenShortFileReads
```

This loads the class compiled from the checked-out source when a test JVM requests
`okio/JvmFileHandle`. It leaves the installed jars and all consumer source unchanged. The agent
prints the exact compiled source-class path each time it substitutes the class. It can also run the
unchanged `urlProgressChannelPreservesConcurrentProducerOrdering` test against the correction.
The consumer must eventually adopt a released source fix; this local substitution is only proof
for review, and is not a production dependency update.

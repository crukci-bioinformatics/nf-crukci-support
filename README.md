# nf-crukci-support

A Nextflow plugin developed at the Cancer Research UK Cambridge Institute that
proactively monitors task log files during workflow execution and provides
utility functions for pipeline authors.

## Summary

The plugin has two main areas of functionality:

**Log scanning**: A background thread monitors running tasks and scans their
`.command.log` files for configurable regex patterns. When a pattern with an
associated exit code is matched, the plugin writes a `.exitcode` file before
Nextflow times out waiting for one. This is particularly useful for tasks killed
by external resource managers such as SLURM's OOM killer, where the scheduler
terminates the process without leaving an exit code for Nextflow to read.

**Extension functions**: Six utility functions available for use in pipeline
scripts, covering Java heap calculation, collection handling, filename
sanitisation and exception logging.

## Get Started

### Requirements

- Nextflow 25.04.0 or newer
- Java 17

### Building from Source

```bash
cd nf-crukci-support
mvn clean package
```

The resulting JAR can be installed manually:

```bash
mkdir -p ~/.nextflow/plugins/nf-crukci-support-1.1.2
cp target/nf-crukci-support-1.1.2.jar \
    ~/.nextflow/plugins/nf-crukci-support-1.1.2/
```

### Enabling the Plugin

Add the plugin to your `nextflow.config`:

```groovy
plugins {
    id 'nf-crukci-support@1.1.2'
}
```

Once declared the log-scanning observer starts automatically. No further
configuration is required to get the default behaviour.

## Log Scanning

The plugin runs a daemon thread that monitors all submitted tasks independently
of the Nextflow executor.

### How it works

Every five seconds the monitor checks each registered task work directory. When it
finds a `.command.log` that has been stable (no modification) for at least two
seconds, but no `.exitcode` file, it scans the log for configured patterns. If
a matching pattern has a non-null exit code the monitor writes that value to
`.exitcode`, giving Nextflow a valid exit status to read instead of timing out
with a "task terminated by external system" error.

### Default patterns

The plugin usually applies these two patterns by default:

| Pattern | Name | Exit code |
|---------|------|-----------|
| `Exceeded job memory limit` | Memory Limit Exceeded | 137 |
| `java.lang.OutOfMemoryError` | Java Heap Exhausted | 137 |

These two patterns are appended to the end of any user-supplied list.
They can be excluded with the `defaultPatterns` configuration option.

### Configuration

All settings go inside a `crukci { }` block in `nextflow.config`.

| Setting | Default | Minimum | Description |
|---------|---------|---------|-------------|
| `javaOverhead` | `64M` | `32M` | Memory reserved for JVM misc overhead (JNI, ByteBuffers, etc.) used by `javaMemoryOptions` |
| `javaMetaspace` | `128M` | `64M` | Memory reserved for JVM metaspace used by `javaMemoryOptions` |
| `maxLinesToScan` | `10000` | - | Maximum lines to read per log file. Set to `0` for unlimited. |
| `defaultPatterns` | `true` | - | Whether to include the two built-in memory-limit patterns. Set to `false` to suppress them. |
| `patterns` | (none) | - | List of patterns to scan for (see below) |

#### Pattern specification

Patterns can be plain strings or maps.

**Plain string** - the string is compiled as a case-sensitive regex. Patterns
whose text contains `"memory limit"` automatically receive exit code 137.

```groovy
patterns = ['Exceeded job memory limit', 'CUDA out of memory']
```

**Map** - provides full control over each pattern:

```groovy
patterns = [
    [
        pattern: 'Out of memory',   // Required - compiled as a regex
        name: 'OOM',                // Optional - display name for log messages
        caseSensitive: false,       // Optional - default is true
        exitCode: 137               // Optional - null means no exit code override
    ]
]
```

### Full configuration example

```groovy
plugins {
    id 'nf-crukci-support@1.1.2'
}

crukci {
    javaOverhead    = '128M'   // Reserve 128 MB for JVM misc overhead
    javaMetaspace   = '256M'   // Reserve 256 MB for JVM metaspace
    maxLinesToScan  = 5000     // Only scan the first 5000 lines
    defaultPatterns = false    // Don't include the default patterns.

    patterns = [
        // Plain string - exit code set automatically because text contains "memory limit"
        'Exceeded job memory limit',

        // Map patterns
        [
            pattern: 'CUDA out of memory',
            name: 'GPU Memory Error',
            caseSensitive: true,
            exitCode: 140
        ],
        [
            pattern: 'error',
            name: 'Generic Error',
            caseSensitive: false,
            exitCode: null          // Detected and logged but exit code not overridden
        ]
    ]
}
```

### Using exit codes in process definitions

```groovy
process myProcess {
    errorStrategy { task.exitStatus == 137 ? 'retry' : 'terminate' }
    maxRetries 3
    memory { task.exitStatus == 137 ? (4.GB * task.attempt) : 4.GB }

    script:
    """
    your_command_here
    """
}
```

## Extension Functions

Six utility functions are provided as Nextflow plugin extension functions.
Include only the ones you need:

```groovy
include { javaMemMB; javaMemoryOptions; sizeOf; makeCollection; safeName; logException } \
    from 'plugin/nf-crukci-support'
```

### javaMemMB(task)

Returns the Java heap size in megabytes for a task, after reserving memory for
JVM metaspace and miscellaneous overhead. The overhead sizes are controlled by
`crukci.javaMetaspace` and `crukci.javaOverhead` in `nextflow.config`.

Throws an exception if the task's memory allocation is too small to leave at
least 16 MB for the heap.

```groovy
process runJavaTool {
    memory '2 GB'

    script:
    def heapMB = javaMemMB(task)
    """
    java -Xmx${heapMB}m -jar myapp.jar
    """
}
```

### javaMemoryOptions(task)

Returns an object with full JVM memory settings derived from the task's memory
allocation. The `jvmOpts` field is ready to paste directly into a Java command
line without quoting.

**Return fields** (all sizes in MB):

| Field | Description |
|-------|-------------|
| `heap` | Java heap size |
| `metaSpace` | Metaspace size |
| `misc` | Miscellaneous overhead |
| `all` | Total task memory (`task.memory.toMega()`) |
| `jvmOpts` | JVM option string: `-XX:MaxMetaspaceSize=…m -Xms…m -Xmx…m` |

```groovy
process runJavaTool {
    memory '4 GB'

    script:
    def mem = javaMemoryOptions(task)
    """
    java ${mem.jvmOpts} -jar myapp.jar
    """
}
```

Override the default overhead sizes in `nextflow.config`:

```groovy
crukci {
    javaMetaspace = '256M'
    javaOverhead  = '128M'
}
```

### sizeOf(thing)

Returns the number of elements in a collection or map, `1` for any other
non-null object, and `0` for `null`. Useful when Nextflow may return either a
single value or a list from a channel.

```groovy
sizeOf([1, 2, 3])      // 3
sizeOf('hello')        // 1
sizeOf(null)           // 0
sizeOf([a: 1, b: 2])   // 2
```

> **Note**: Nextflow >= 23.9 introduced the `arity` attribute on `file`/`path`
> and the `files()` function as first-class alternatives. `sizeOf` remains
> available for pipelines that predate those features.

### makeCollection(thingOrList)

Wraps a single value in a list so downstream code can always treat the result
as a collection. Returns the original collection unchanged, or `null` if the
input is `null`.

This is particularly important for `Path` objects: calling `.size()` on a
single `Path` returns the file size in bytes, not `1`. Wrapping with
`makeCollection` ensures `.size()` returns the count of files.

```groovy
makeCollection('file.txt')          // ['file.txt']
makeCollection(['a.txt', 'b.txt'])  // ['a.txt', 'b.txt']
makeCollection(null)                // null
```

### safeName(name)

Converts a string into a filesystem-safe name.

- Alphanumeric characters, `.`, `_`, and `-` are kept unchanged.
- Spaces and tabs are removed.
- All other characters are replaced with `_`.

**Requires** Apache Commons Lang3 on the classpath. Place the JAR in a `lib/`
directory inside your pipeline:

To build the plugin:
```bash
mkdir -p lib
wget -P lib \
  https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar
```

```groovy
safeName('My Sample: Run 1 (2026)')  // 'MySample_Run1_2026_'
safeName('Data/File.txt')            // 'Data_File.txt'
safeName("Tab\tRemoved")             // 'TabRemoved'
```

### logException(exception)

Logs a `Throwable` at ERROR level with its full stack trace, then re-throws it.
If the exception is an `InvocationTargetException` the underlying cause is
logged instead.

```groovy
try {
    riskyOperation()
} catch (Exception e) {
    logException(e)
}
```

## Examples

### Memory limit retry with log scanning

```groovy
plugins {
    id 'nf-crukci-support@1.1.2'
}

// crukci { } block not required - default patterns cover the common cases

process alignReads {
    errorStrategy { task.exitStatus == 137 ? 'retry' : 'terminate' }
    maxRetries 3
    memory { 8.GB * task.attempt }

    input:
    path reads

    script:
    """
    bwa mem ref.fa ${reads} > aligned.sam
    """
}
```

When SLURM kills the job for exceeding its memory limit the log will contain
`Exceeded job memory limit`. The plugin writes exit code 137 to `.exitcode`,
Nextflow reads that as the task exit status, and the `errorStrategy` triggers a
retry with doubled memory.

### Java tool with automatic memory sizing

```groovy
include { javaMemoryOptions } from 'plugin/nf-crukci-support'

process runPicard {
    memory '8 GB'

    input:
    path bam

    output:
    path 'metrics.txt'

    script:
    def mem = javaMemoryOptions(task)
    """
    java ${mem.jvmOpts} -jar picard.jar CollectAlignmentSummaryMetrics \
        INPUT=${bam} OUTPUT=metrics.txt
    """
}
```

### Safe output filenames

```groovy
include { safeName } from 'plugin/nf-crukci-support'

process createReport {
    input:
    val sampleName

    output:
    path "${safeName(sampleName)}_report.html"

    script:
    """
    generate_report.py --sample "${sampleName}" \
        --output "${safeName(sampleName)}_report.html"
    """
}
```

## Building and Testing

The project supports both Maven and Gradle builds.

### Maven

```bash
# Build
mvn clean package

# Run tests
mvn test
```

### Gradle

```bash
# Build
./gradlew assemble

# Run tests
./gradlew test
```

> **Note:** Two fixes in `build.gradle` are required for Gradle tests to work correctly
> with Gradle 8 and Nextflow 25.x:
>
> 1. `compileTestJava` and `compileTestGroovy` must explicitly depend on the
>    `extensionPoints` task, because Gradle 8's strict task-ordering validation
>    rejects the implicit dependency that earlier versions allowed.
>
> 2. `org.junit.platform:junit-platform-launcher` must be pinned to `1.14.1`
>    (matching `junit-platform-engine`) via a `testRuntimeOnly` dependency.
>    Nextflow's transitive `groovy-test-junit5` dependency resolves the launcher
>    to `1.13.3`, which is misaligned with the engine version and causes JUnit
>    test discovery to fail at runtime.

## License

Developed at the Cancer Research UK Cambridge Institute.

## Nextflow Documentation Links

1. https://docs.seqera.io/nextflow/plugins/developing-plugins - Writing plugins
2. https://docs.seqera.io/nextflow/guides/gradle-plugin - Gradle plugin
3. https://docs.seqera.io/nextflow/guides/migrate-plugin - Plugin registry

## Authors

Richard Bowers (richard.bowers@cruk.cam.ac.uk)

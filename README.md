# nf-crukci-support

A Nextflow plugin that scans task log files for configurable regex patterns and handles memory limit issues.

## Overview

This plugin monitors Nextflow task completions and scans their `.command.log` files for configurable regex patterns. It runs a background TaskMonitor thread that proactively detects tasks killed by SLURM or external systems (such as memory limit violations) and creates appropriate `.exitcode` files before Nextflow times out waiting for them.

The plugin is particularly useful for detecting and handling memory limit violations by triggering task retries with increased memory.

## Features

- Scans task log files when tasks are killed by external systems
- Configurable regex patterns with case-sensitive/insensitive matching
- **Exit code override**: Set custom exit codes when patterns match to trigger Nextflow errorStrategy
- **Proactive monitoring**: Background TaskMonitor thread detects tasks killed externally (SLURM OOM, GPU limits, etc.)
- Automatic detection of "memory limit" patterns with exit code 137
- Configurable maximum lines to scan
- Verbose logging mode for debugging

## Installation

1. Build the plugin:
```bash
mvn clean package
```

2. Install to Nextflow plugins directory:
```bash
mkdir -p ~/.nextflow/plugins/nf-crukci-support-1.0.0-SNAPSHOT
cp target/nf-crukci-support-1.0.0-SNAPSHOT.jar ~/.nextflow/plugins/nf-crukci-support-1.0.0-SNAPSHOT/
```

## Configuration

Add the plugin to your `nextflow.config`:

```groovy
plugins {
    id 'nf-crukci-support@<version>'
}

logScan {
    enabled        = true
    scanOnSuccess  = false    // Scan logs of successful tasks
    scanOnFailure  = true     // Scan logs of failed tasks
    maxLinesToScan = 10000    // 0 = unlimited
    verbose        = false    // Enable debug logging

    patterns = [
        // Simple string pattern (auto-detects memory limit)
        'Exceeded job memory limit',

        // Detailed pattern configuration
        [
            pattern: 'ERROR',
            name: 'Error Pattern',
            caseSensitive: true,
            exitCode: null              // Optional: override exit code
        ],
        [
            pattern: 'warning',
            name: 'Warning Pattern',
            caseSensitive: false,
            exitCode: null
        ],
        [
            pattern: 'CUDA out of memory',
            name: 'GPU Memory Error',
            caseSensitive: true,
            exitCode: 140               // Custom exit code for GPU memory
        ]
    ]
}
```

### Getting the Plugin

At present this plugin is not part of the set of Nextflow plugins globally available. The only way to have the Nextflow automatically download the
plugin for you is to set the `NXF_PLUGINS_TEST_REPOSITORY` environment variable to include our own `plugins.json`.

```BASH
export NXF_PLUGINS_TEST_REPOSITORY="https://github.com/crukci-bioinformatics/nextflow-plugins/raw/refs/heads/master/plugins.json,https://raw.githubusercontent.com/nextflow-io/plugins/main/plugins.json"
```

Probably best put this into your `.bashrc`. If we put this plugin into the Nextflow main plugins file this will no longer be required, but we need
to be sure it works properly before approaching the Nextflow people.

## Usage

The plugin automatically monitors tasks when enabled. It uses a background thread to detect patterns in task logs and create exit code files for tasks killed by external systems.

### Exit Code Handling Mechanism

The plugin employs a **proactive monitoring approach** using a background TaskMonitor thread:

#### TaskMonitor Background Thread

A background daemon thread monitors all submitted tasks independently:
- Checks every 5 seconds for tasks that have log files but no `.exitcode` file
- Detects tasks killed by SLURM (OOM), resource managers, or external signals
- Proactively creates `.exitcode` files before Nextflow times out
- Prevents "task terminated by external system" errors

**When it works:**
- Tasks killed by SLURM for exceeding memory limits
- Tasks killed for GPU memory violations
- Tasks terminated by external signals (SIGKILL, etc.)

**Detection criteria:**
- Task has been submitted (has a work directory)
- `.command.log` exists and is stable (no modifications for 2+ seconds)
- No `.exitcode` file exists yet
- Log contains a pattern with a non-null `exitCode` value

**Timing:**
- 5-second check interval (0-10 second detection window)
- Only runs during workflow execution (stops when workflow completes)

### Exit Code Override

When a pattern is matched, the plugin can set a custom exit code for the task. This is particularly useful for triggering specific error strategies:

- **Exit code 137**: Commonly used for memory limit violations (automatically set for patterns containing "memory limit")
- **Custom exit codes**: Set any exit code to trigger different error handling behaviors

Configure your process with an error strategy to handle these exit codes:

```groovy
process myProcess {
    errorStrategy { task.exitStatus == 137 ? 'retry' : 'terminate' }
    maxRetries 3
    memory { task.exitStatus == 137 ? 4.GB * task.attempt : 4.GB }

    script:
    """
    your_command_here
    """
}
```

When the plugin detects a pattern with `exitCode: 137` in the log file, it sets the task's exit status to 137, triggering the retry logic with increased memory.

## Pattern Configuration

Patterns can be specified in two ways:

### Simple String Pattern
```groovy
patterns = ['ERROR', 'WARNING']
```

Patterns containing "memory limit" automatically trigger retry detection.

### Detailed Map Configuration
```groovy
patterns = [
    [
        pattern: 'regex_pattern',      // Required: regex pattern to match
        name: 'Pattern Name',          // Optional: display name
        caseSensitive: true,           // Optional: default is true
        triggerRetry: false,           // Optional: mark as retry trigger
        exitCode: null                 // Optional: exit code to set when matched
    ]
]
```

**Exit code behavior:**
- Patterns containing "memory limit" automatically get `exitCode: 137` (if not explicitly set)
- Set `exitCode: null` to not override the task's exit status
- Any integer exit code can be specified (0-255 recommended)

## Default Behavior

If no patterns are configured, the plugin uses a default pattern:
- Pattern: `Exceeded job memory limit`
- Name: `Memory Limit Exceeded`
- Triggers retry detection
- Exit code: `137` (standard exit code for memory limit violations)

## Extension Functions

The plugin provides utility functions that can be used in your Nextflow pipelines. These functions provide helper functionality for memory management, collection handling, and debugging.

### Including Extension Functions

To use the extension functions in your pipeline, add an `include` statement at the top of your `.nf` file:

```groovy
include { javaMemMB; javaMemoryOptions; sizeOf; makeCollection; safeName; logException } from 'plugin/nf-crukci-support'
```

You can include only the functions you need:

```groovy
include { javaMemMB; safeName } from 'plugin/nf-crukci-support'
```

### Available Functions

#### javaMemMB(task)

Calculates the Java heap size based on the task memory allocation, reserving 128 MB for JVM overhead.

**Parameters:**
- `task` - The task object containing memory allocation information

**Returns:** The calculated Java heap size in megabytes

**Throws:** Exception if insufficient memory is available after overhead (minimum 16 MB heap required)

**Example:**
```groovy
process myJavaProcess {
    memory '2 GB'

    script:
    def heapMB = javaMemMB(task)
    """
    java -Xmx${heapMB}m -jar myapp.jar
    """
}
```

#### javaMemoryOptions(task)

Provides comprehensive OpenJDK JVM memory configuration based on the task memory allocation. This function allocates memory for the Java heap, metaspace, and miscellaneous overhead.

**Parameters:**
- `task` - The task object containing memory allocation information

**Configuration parameters** (via `params`):
- `java_metaspace_size` - Maximum metaspace size in MB (default: 128, minimum: 64)
- `java_overhead_size` - Additional overhead for JNI, ByteBuffers, etc. in MB (default: 64, minimum: 32)

Both parameters are multiplied by `task.attempt` to scale with retry attempts.

**Returns:** An object with the following fields:
- `heap` - The heap size in MB
- `metaSpace` - The metaspace size in MB
- `misc` - The additional overhead in MB
- `all` - The total task memory allocation in MB
- `jvmOpts` - A string containing JVM options to set the calculated memory values

**Example:**
```groovy
process myJavaProcess {
    memory '4 GB'

    script:
    def mem = javaMemoryOptions(task)
    """
    echo "Heap: ${mem.heap} MB, MetaSpace: ${mem.metaSpace} MB, Total: ${mem.all} MB"
    java ${mem.jvmOpts} -jar myapp.jar
    """
}
```

**Configuration example:**
```groovy
params {
    java_metaspace_size = 256
    java_overhead_size = 128
}
```

#### sizeOf(thing)

Gets the size of a collection, map, or single object. Handles the common issue where Nextflow sometimes returns a single item instead of a collection.

**Parameters:**
- `thing` - The object whose size is to be determined

**Returns:**
- The size of the collection or map
- `1` for non-collection objects
- `0` for `null`

**Example:**
```groovy
def list = [1, 2, 3]
def single = "test"
def nullVal = null

println "Size of list: ${sizeOf(list)}"        // 3
println "Size of single: ${sizeOf(single)}"    // 1
println "Size of null: ${sizeOf(nullVal)}"     // 0
```

**See also:** Nextflow >= 23.9 provides the `arity` attribute on `file` and `path`, and the `files()` function as alternatives.

#### makeCollection(thingOrList)

Ensures that an object is wrapped in a collection. Useful for handling cases where Nextflow might return either a single item or a collection.

**Parameters:**
- `thingOrList` - The object to ensure is a collection

**Returns:**
- The original collection if input is already a collection
- A single-item list containing the object if input is not a collection
- `null` if input is `null`

**Example:**
```groovy
def single = "file.txt"
def collection = makeCollection(single)
println collection  // ["file.txt"]

def list = ["a.txt", "b.txt"]
def collection2 = makeCollection(list)
println collection2  // ["a.txt", "b.txt"]
```

**See also:** Nextflow >= 23.9 provides the `arity` attribute and `files()` function as alternatives.

#### safeName(name)

Sanitizes a string to make it safe for use as a filename. Converts unsafe characters to underscores and removes spaces.

**Parameters:**
- `name` - The name to make safe for use as a filename

**Returns:** A sanitized filename string

**Rules:**
- Alphanumeric characters, dots (`.`), underscores (`_`), and hyphens (`-`) are preserved
- Spaces and tabs are removed
- All other characters are converted to underscores

**Requirements:** This function requires Apache Commons Lang3. Add the JAR to a `lib/` directory in your pipeline:
```bash
mkdir -p lib
wget -P lib https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar
```

**Example:**
```groovy
def unsafe = "My File: Test (v1.0)"
def safe = safeName(unsafe)
println safe  // "MyFile_Testv1.0"

process createOutput {
    script:
    def sampleName = "Sample #123 (batch-1)"
    def outputFile = "${safeName(sampleName)}.txt"
    """
    echo "Processing ${sampleName}" > ${outputFile}
    """
}
```

#### logException(exception)

Logs an exception to the logger with full stack trace and then re-throws it. Automatically unwraps `InvocationTargetException` to log the underlying cause.

**Parameters:**
- `exception` - The exception to log

**Throws:** The original exception after logging

**Example:**
```groovy
try {
    // Some operation that might fail
    riskyOperation()
} catch (Exception e) {
    logException(e)
}
```

This is particularly useful for debugging complex workflows where exceptions might be wrapped or difficult to trace.

## Requirements

- Nextflow 25.04.0 or newer (tested with 25.04.4)
- Java 21

**Note**: While this plugin is built with Java 21, it does not use the Java Platform Module System (JPMS) because Nextflow itself is not modular. The plugin uses the traditional classpath mechanism for compatibility.

## Building

```bash
mvn clean package
```

## Testing

```bash
mvn test
```

## License

Developed at the Cancer Research UK Cambridge Institute.

## Authors

Richard Bowers (richard.bowers@cruk.cam.ac.uk)

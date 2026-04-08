# nf-crukci-support Example Pipeline

This directory contains example Nextflow pipelines demonstrating the features of the nf-crukci-support plugin.

## Example Files

- `example-pipeline.nf` - Demonstrates the log scanning functionality
- `example.config` - Configuration for log scanning
- `extension-example.nf` - Demonstrates the CRUK CI extension functions
- `extension-example.config` - Configuration for extension functions

## What It Demonstrates

### Log Scanning Features (example-pipeline.nf)

1. **Exit code override** - When patterns match, the plugin can override the task's exit code
2. **Automatic memory detection** - Patterns containing "memory limit" automatically get exit code 137
3. **Case-insensitive matching** - Configure patterns to be case-sensitive or case-insensitive
4. **Retry triggering** - Use exit codes to trigger specific error handling strategies
5. **Pattern naming** - Assign meaningful names to patterns for better logging
6. **Scanning control** - Choose to scan successful tasks, failed tasks, or both

### Extension Functions (extension-example.nf)

1. **javaMemMB(task)** - Calculate Java heap size with overhead
2. **javaMemoryOptions(task)** - Full JVM memory configuration (heap, metaspace, options)
3. **sizeOf(thing)** - Get size of collections, maps, or single values
4. **makeCollection(thing)** - Ensure a value is a collection
5. **safeName(name)** - Convert strings to safe filenames
6. **logException(e)** - Log exceptions with full stack traces

## Running the Examples

### 1. Build and Install the Plugin

From the repository root:

```bash
cd nf-crukci-support
mvn clean package
mkdir -p ~/.nextflow/plugins/nf-crukci-support-1.0-SNAPSHOT
cp target/nf-crukci-support-1.0-SNAPSHOT.jar ~/.nextflow/plugins/nf-crukci-support-1.0-SNAPSHOT/
```

### 2. Run the Log Scanning Example

```bash
cd examples
nextflow run example-pipeline.nf -c example.config
```

### 3. Run the Extension Functions Example

```bash
cd examples
nextflow run extension-example.nf -c extension-example.config
```

## Using Extension Functions in Your Pipeline

### Include the Functions

```groovy
// Include all functions
include { javaMemMB; javaMemoryOptions; sizeOf; makeCollection; safeName; logException } from 'plugin/nf-crukci-support'

// Or include only what you need
include { javaMemoryOptions; safeName } from 'plugin/nf-crukci-support'
```

### Example Usage

```groovy
process runJavaTool {
    memory '4 GB'
    
    script:
    // Get Java memory options
    def mem = javaMemoryOptions(task)
    
    """
    java ${mem.jvmOpts} -jar tool.jar
    """
}

process processSamples {
    input:
    val samples
    
    script:
    // Ensure samples is always a collection
    def sampleList = makeCollection(samples)
    def count = sizeOf(sampleList)
    
    """
    echo "Processing ${count} samples"
    for sample in ${sampleList.join(' ')}; do
        echo "Sample: \$sample"
    done
    """
}

process saveResults {
    input:
    val name
    path results
    
    script:
    // Make filename safe
    def safeName = safeName(name)
    
    """
    cp ${results} output_${safeName}.txt
    """
}
```

## Expected Behavior

### Log Scanning Example

When you run the pipeline, you should observe:

1. **successTask** - Completes successfully, no scanning (scanOnSuccess = false)

2. **successWithWarnings** - Completes successfully but contains WARNING in log
   - Will be scanned if you set `scanOnSuccess = true`

3. **memoryLimitTask** - Initially fails with exit code 1
   - Plugin detects "Exceeded job memory limit" pattern
   - Sets exit code to 137
   - Triggers retry with increased memory (2GB)
   - Should succeed on retry

4. **outOfMemoryTask** - Initially fails with exit code 1
   - Plugin detects "Out Of Memory" pattern (case-insensitive)
   - Sets exit code to 137
   - Triggers retry with increased memory (4GB)
   - Should succeed on retry

5. **genericErrorTask** - Fails with ERROR pattern
   - Plugin detects pattern but does NOT override exit code (exitCode: null)
   - Error is ignored (errorStrategy 'ignore')

### Extension Functions Example

When you run the extension example, you should see:

```
=== Testing CRUK CI Extension Functions ===

1. Testing sizeOf:
   sizeOf([1, 2, 3]) = 3
   sizeOf('test') = 1
   sizeOf(null) = 0

2. Testing makeCollection:
   makeCollection('file.txt') = [file.txt]
   makeCollection(['a.txt', 'b.txt']) = [a.txt, b.txt]

3. Testing safeName:
   safeName('My File: Test (v1.0)') = 'MyFile_Test_v1.0'

=== All extension functions work correctly ===
```

## Configuration Options

The `example.config` file demonstrates all available configuration options:

```groovy
logScan {
    enabled        = true       // Enable/disable the plugin
    scanOnSuccess  = false      // Scan successful task logs
    scanOnFailure  = true       // Scan failed task logs
    maxLinesToScan = 10000      // Maximum lines to scan (0 = unlimited)
    verbose        = true       // Enable verbose logging

    patterns = [
        // Simple string pattern
        'Exceeded job memory limit',

        // Detailed pattern with all options
        [
            pattern: 'ERROR',           // Regex pattern to match
            name: 'Generic Error',      // Display name
            caseSensitive: true,        // Case sensitivity
            exitCode: null              // Exit code override (null = no override)
        ]
    ]
}
```

## Customizing the Examples

Try modifying the configuration to see different behaviors:

1. Set `scanOnSuccess = true` to see WARNING pattern detection in successful tasks
2. Set `verbose = false` to reduce logging output
3. Add your own patterns to detect
4. Change exit codes to trigger different error handling strategies
5. Adjust `maxLinesToScan` to limit scanning

## Extension Function Details

### javaMemMB(task)

Simple Java heap calculation with 128MB overhead:

```groovy
def heapMB = javaMemMB(task)
// Returns: task.memory.toMega() - 128
```

### javaMemoryOptions(task)

Comprehensive JVM memory configuration:

```groovy
def mem = javaMemoryOptions(task)
// Returns object with:
//   mem.heap       - Heap size in MB
//   mem.metaSpace  - Metaspace size in MB
//   mem.misc       - Other overhead in MB
//   mem.all        - Total task memory in MB
//   mem.jvmOpts    - JVM options string

// Use in script:
"""
java ${mem.jvmOpts} -jar app.jar
"""
```

Configure via params:
```groovy
params {
    java_metaspace_size = 128  // MB per attempt
    java_overhead_size = 64    // MB per attempt
}
```

### sizeOf(thing)

Safely get the size of anything:

```groovy
sizeOf([1, 2, 3])  // Returns 3
sizeOf("hello")    // Returns 1
sizeOf(null)       // Returns 0
sizeOf([a: 1])     // Returns 1 (map size)
```

### makeCollection(thing)

Ensure a value is always a collection:

```groovy
makeCollection("single.txt")      // Returns ["single.txt"]
makeCollection(["a.txt", "b.txt"]) // Returns ["a.txt", "b.txt"]
makeCollection(null)              // Returns null
```

### safeName(name)

Convert strings to filesystem-safe names:

```groovy
safeName("Sample: Test (v1.0)")  // Returns "Sample_Test_v1.0"
safeName("Data/File.txt")        // Returns "Data_File.txt"
safeName("Tab\tSpace Name")      // Returns "TabSpaceName"
```

Rules:
- Alphanumeric, dot, underscore, hyphen: kept as-is
- Spaces and tabs: removed
- Everything else: converted to underscore

**Note:** Requires commons-lang3 in your pipeline's lib directory.

### logException(e)

Log exceptions with full stack trace:

```groovy
try {
    riskyOperation()
} catch (Exception e) {
    logException(e)  // Logs full trace, then re-throws
}
```

Handles InvocationTargetException specially to log the real cause.

## Troubleshooting

If the plugin doesn't seem to be working:

1. Check that the plugin JAR is in the correct location
2. Verify the plugin ID in your config matches: `nf-crukci-support@1.0-SNAPSHOT`
3. Enable verbose logging (`verbose = true`) to see detailed scan information
4. Check that patterns are valid regex expressions
5. Ensure you're running Nextflow 25.04.0 or newer

## Learn More

See the main [README.md](../README.md) for complete documentation on the plugin architecture and features.

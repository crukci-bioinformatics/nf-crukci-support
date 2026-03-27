# nf-crukci-logscan Example Pipeline

This directory contains an example Nextflow pipeline demonstrating the features of the nf-crukci-logscan plugin.

## What It Demonstrates

The example pipeline showcases the following plugin capabilities:

1. **Exit code override** - When patterns match, the plugin can override the task's exit code
2. **Automatic memory detection** - Patterns containing "memory limit" automatically get exit code 137
3. **Case-insensitive matching** - Configure patterns to be case-sensitive or case-insensitive
4. **Retry triggering** - Use exit codes to trigger specific error handling strategies
5. **Pattern naming** - Assign meaningful names to patterns for better logging
6. **Scanning control** - Choose to scan successful tasks, failed tasks, or both

## Files

- `example-pipeline.nf` - The example pipeline with various task types
- `example.config` - Configuration file showing all plugin options

## Running the Example

### 1. Build and Install the Plugin

From the repository root:

```bash
cd nf-crukci-support
mvn clean package
mkdir -p ~/.nextflow/plugins/nf-crukci-logscan-1.0.0-SNAPSHOT
cp target/nf-crukci-logscan-1.0.0-SNAPSHOT.jar ~/.nextflow/plugins/nf-crukci-logscan-1.0.0-SNAPSHOT/
```

### 2. Run the Example Pipeline

```bash
cd examples
nextflow run example-pipeline.nf -c example.config
```

## Expected Behavior

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

## Example Output

You should see warning messages like:

```
WARN  Task 'memoryLimitTask (1)' - Pattern 'Exceeded job memory limit' found at line 5: ERROR: Exceeded job memory limit
WARN  Task 'memoryLimitTask (1)' - Setting exit code to 137 due to pattern match
WARN  Task 'outOfMemoryTask (1)' - Pattern 'OOM Pattern' found at line 3: Process failed: Out Of Memory
WARN  Task 'outOfMemoryTask (1)' - Setting exit code to 137 due to pattern match
WARN  Task 'genericErrorTask (1)' - Pattern 'Generic Error' found at line 3: ERROR: File not found
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

## Customizing the Example

Try modifying the configuration to see different behaviors:

1. Set `scanOnSuccess = true` to see WARNING pattern detection in successful tasks
2. Set `verbose = false` to reduce logging output
3. Add your own patterns to detect
4. Change exit codes to trigger different error handling strategies
5. Adjust `maxLinesToScan` to limit scanning

## Exit Code Strategy

The plugin uses exit codes to trigger Nextflow's built-in error handling:

- **Exit code 137** - Commonly used for memory limit violations, triggers memory-based retry
- **Exit code 140** - Example custom exit code for GPU memory issues
- **null** - No exit code override, uses the task's actual exit code

Configure your process error strategy to handle these codes:

```groovy
process {
    errorStrategy = {
        task.exitStatus == 137 ? 'retry' :
        task.exitStatus == 140 ? 'retry' :
        'terminate'
    }
    memory = { task.exitStatus == 137 ? 4.GB * task.attempt : 4.GB }
}
```

## Troubleshooting

If the plugin doesn't seem to be working:

1. Check that the plugin JAR is in the correct location
2. Verify the plugin ID in your config matches: `nf-crukci-logscan@1.0.0-SNAPSHOT`
3. Enable verbose logging (`verbose = true`) to see detailed scan information
4. Check that patterns are valid regex expressions
5. Ensure you're running Nextflow 25.04.0 or newer

## Learn More

See the main [README.md](../README.md) for complete documentation on the plugin architecture and features.

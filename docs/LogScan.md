# Log Scanning

This plugin monitors Nextflow tasks and proactively handles tasks killed by external systems (such as SLURM memory limits). It runs a background TaskMonitor thread that scans `.command.log` files for configurable regex patterns and creates appropriate `.exitcode` files before Nextflow times out waiting for them.

The plugin is particularly useful for detecting and handling memory limit violations by triggering task retries with increased memory.

## Configuration

Add the plugin to your `nextflow.config`:

```groovy
plugins {
    id 'nf-crukci-support@<version>'
}

logScan {
    maxLinesToScan = 10000    // 0 = unlimited

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

## Usage

The plugin automatically monitors tasks. It uses a background thread to detect patterns in task logs and create exit code files for tasks killed by external systems.

### TaskMonitor Background Thread

The core functionality is provided by a background daemon thread that monitors all submitted tasks independently:

- **Checks every 5 seconds** for tasks that have log files but no `.exitcode` file
- **Detects tasks killed by SLURM** (OOM), resource managers, or external signals
- **Proactively creates `.exitcode` files** before Nextflow times out
- **Prevents "task terminated by external system" errors**

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
        exitCode: null                 // Optional: exit code to set when matched
    ]
]
```

**Exit code behavior:**
- Patterns containing "memory limit" automatically get `exitCode: 137` (if not explicitly set)
- Set `exitCode: null` to not override the task's exit status
- Any integer exit code can be specified (0-255 recommended)

## Default Behavior

If no patterns are configured, the plugin uses two default patterns:

  - Pattern 1 (catching SLURM process killing):
    - Pattern: "`Exceeded job memory limit`"
    - Name: "Memory Limit Exceeded"
    - Exit code: 137
  - Pattern 2 (Java heap exhaustion)
    - Pattern: "`java.lang.OutOfMemoryError`"
    - Name: "Java Heap Exhausted"
    - Exit code: 137

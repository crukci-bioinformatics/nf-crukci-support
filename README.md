# nf-crukci-logscan

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
mkdir -p ~/.nextflow/plugins/nf-crukci-logscan-1.0.0-SNAPSHOT
cp target/nf-crukci-logscan-1.0.0-SNAPSHOT.jar ~/.nextflow/plugins/nf-crukci-logscan-1.0.0-SNAPSHOT/
```

## Configuration

Add the plugin to your `nextflow.config`:

```groovy
plugins {
    id 'nf-crukci-logscan@1.0.0-SNAPSHOT'
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

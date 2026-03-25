# nf-crukci-logscan

A Nextflow plugin that scans task log files for configurable regex patterns and handles memory limit issues.

## Overview

This plugin monitors Nextflow task completions and scans their `.command.log` files for configurable regex patterns. It's particularly useful for detecting and handling memory limit violations by triggering task retries.

## Features

- Scans task log files on completion (success and/or failure)
- Configurable regex patterns with case-sensitive/insensitive matching
- Automatic detection of "Exceeded job memory limit" patterns
- Support for triggering task retries on specific patterns
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
cp target/crukci-logscan-1.0.0-SNAPSHOT.jar ~/.nextflow/plugins/nf-crukci-logscan-1.0.0-SNAPSHOT/
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
            triggerRetry: false
        ],
        [
            pattern: 'warning',
            name: 'Warning Pattern',
            caseSensitive: false,
            triggerRetry: false
        ]
    ]
}
```

## Usage

The plugin automatically scans task log files when enabled. To enable task retries when memory limits are exceeded, configure your process with an error strategy:

```groovy
process myProcess {
    errorStrategy 'retry'
    maxRetries 3
    memory { 4.GB * task.attempt }
    
    script:
    """
    your_command_here
    """
}
```

When the plugin detects "Exceeded job memory limit" in the log file, it will log a warning. Combined with the `errorStrategy 'retry'` configuration and dynamic memory allocation, tasks will automatically retry with more memory.

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
        triggerRetry: false            // Optional: mark as retry trigger
    ]
]
```

## Default Behavior

If no patterns are configured, the plugin uses a default pattern:
- Pattern: `Exceeded job memory limit`
- Name: `Memory Limit Exceeded`
- Triggers retry detection

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

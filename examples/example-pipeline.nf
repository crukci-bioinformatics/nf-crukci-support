#!/usr/bin/env nextflow

/*
 * Example pipeline demonstrating the nf-crukci-support plugin
 *
 * This pipeline shows how the plugin:
 * - Scans task log files for configured patterns
 * - Overrides exit codes based on pattern matches
 * - Triggers retries with increased resources for memory errors
 */

println """
================================================================================
nf-crukci-support Example Pipeline
================================================================================
This pipeline demonstrates log scanning and exit code override functionality.
Watch for log scan warnings in the output.
================================================================================
"""

// Process that will succeed
process successTask {
    output:
    path 'output.txt'

    script:
    """
    echo "This task will succeed"
    echo "Processing data..."
    echo "Done!"
    echo "Success" > output.txt
    """
}

// Process that fails but with exit code override from log pattern
// The plugin will detect "Exceeded job memory limit" in the log,
// set exit code to 137, and trigger a retry with more memory
process memoryLimitTask {
    // Error strategy checks exit code
    errorStrategy { task.exitStatus == 137 ? 'retry' : 'terminate' }
    maxRetries 2
    memory { 1.GB * task.attempt }

    script:
    """
    echo "Starting task with ${task.memory} memory..."
    echo "Processing large dataset..."

    # Simulate a memory limit error
    # The plugin will detect this pattern and set exit code to 137
    if [ ${task.attempt} -lt 2 ]; then
        echo "ERROR: Exceeded job memory limit"
        echo "Current memory: ${task.memory}"
        exit 1
    else
        echo "Successfully completed with ${task.memory}"
    fi
    """
}

// Process that will succeed but produce warnings in log
// Demonstrates scanning successful tasks when scanOnSuccess = true
process successWithWarnings {
    script:
    """
    echo "Starting task..."
    echo "WARNING: Resource usage is high"
    echo "Task completed successfully"
    """
}

// Process that fails with a generic ERROR pattern
// Exit code will NOT be overridden (exitCode: null in config)
process genericErrorTask {
    errorStrategy 'ignore'

    script:
    """
    echo "Starting task..."
    echo "Processing data..."
    echo "ERROR: File not found"
    exit 1
    """
}

// Process that simulates an out-of-memory error (case-insensitive)
// Demonstrates case-insensitive pattern matching with exit code 137
process outOfMemoryTask {
    errorStrategy { task.exitStatus == 137 ? 'retry' : 'terminate' }
    maxRetries 2
    memory { 2.GB * task.attempt }

    script:
    """
    echo "Allocating memory..."

    if [ ${task.attempt} -lt 2 ]; then
        echo "Process failed: Out Of Memory"
        exit 1
    else
        echo "Completed with sufficient memory: ${task.memory}"
    fi
    """
}

workflow {
    successTask()
    successWithWarnings()
    memoryLimitTask()
    outOfMemoryTask()
    genericErrorTask()
}

workflow.onComplete {
    println """
    ================================================================================
    Pipeline completed!
    ================================================================================
    Check the output above for log scan warnings showing pattern matches.
    The memory tasks should have retried with increased memory allocation.
    ================================================================================
    """
}

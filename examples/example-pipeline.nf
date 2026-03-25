#!/usr/bin/env nextflow

/*
 * Example pipeline demonstrating the nf-crukci-logscan plugin
 */

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

// Process that will fail with a memory error message
process memoryFailTask {
    errorStrategy 'retry'
    maxRetries 2
    memory { 1.GB * task.attempt }

    script:
    """
    echo "Starting task..."
    echo "Processing large dataset..."
    echo "ERROR: Exceeded job memory limit"
    echo "Task failed"
    exit 1
    """
}

// Process that will fail with a generic error
process genericFailTask {
    errorStrategy 'ignore'

    script:
    """
    echo "Starting task..."
    echo "ERROR: Something went wrong"
    exit 1
    """
}

workflow {
    successTask()
    memoryFailTask()
    genericFailTask()
}

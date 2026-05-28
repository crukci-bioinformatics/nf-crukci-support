#!/usr/bin/env nextflow

nextflow.enable.dsl = 2

process overtime
{
    time { 15.s * task.attempt }
    maxRetries 6

    input:
        val(v)

    shell:
        secsToWait = 30

        """
        #!/bin/bash

        echo "Waiting for !{secsToWait} seconds."
        sleep !{secsToWait}s
        echo "Time's up: all done"
        exit 0
        """
}

process overmemory
{
    // task.memory.toMega() is how to access memory given.
    memory { 32.MB * task.attempt }
    maxRetries 1

    input:
        val(v)

    shell:
        """
        grabandwait 48 60
        """
}

workflow
{
    vars = channel.of([7])

    overtime(vars)
    overmemory(vars)
}

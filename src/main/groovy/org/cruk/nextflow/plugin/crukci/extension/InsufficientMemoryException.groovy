package org.cruk.nextflow.plugin.crukci.extension

import org.cruk.nextflow.plugin.crukci.CRUKCIConfig

import nextflow.util.MemoryUnit

class InsufficientMemoryException extends RuntimeException
{
    private MemoryUnit taskAllocation
    private MemoryUnit overheads

    InsufficientMemoryException(MemoryUnit taskAllocation, MemoryUnit overhead, MemoryUnit metaspace)
    {
        this(taskAllocation, overhead.plus(metaspace))
    }

    private InsufficientMemoryException(MemoryUnit taskAllocation, MemoryUnit totalOverhead)
    {
        super("Insufficent memory for this Java task to run. Task is allocated ${taskAllocation}; overheads are ${totalOverhead}; " +
              "minimum size for Java heap is ${CRUKCIConfig.MINIMUM_JAVA_HEAP}. " +
              "The task needs at least ${totalOverhead.plus(CRUKCIConfig.MINIMUM_JAVA_HEAP)} allocated.")
        this.taskAllocation = taskAllocation
        this.overheads = totalOverhead
    }

    public MemoryUnit getTaskAllocation()
    {
        return taskAllocation
    }

    public MemoryUnit getOverheads()
    {
        return overheads
    }
}

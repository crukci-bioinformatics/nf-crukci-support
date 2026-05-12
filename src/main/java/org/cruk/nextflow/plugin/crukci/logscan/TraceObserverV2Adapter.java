package org.cruk.nextflow.plugin.crukci.logscan;

import nextflow.Session;
import nextflow.processor.TaskProcessor;
import nextflow.trace.TraceObserverV2;
import nextflow.trace.event.FilePublishEvent;
import nextflow.trace.event.TaskEvent;
import nextflow.trace.event.WorkflowOutputEvent;

/**
 * No-op adapter for the {@code TraceObserverV2} interface. This is its
 * own class to keep the real implementations of TraceObserverV2
 * cleaner, with only the methods they need to implement needed in those
 * classes. Though the Groovy source for TraceObserverV2 defines all
 * methods as <i>default</i>, this doesn't seem to carry through to
 * the compiled Java code.
 *
 * @author Richard Bowers
 */
public class TraceObserverV2Adapter implements TraceObserverV2
{
    /**
     * Constructor.
     */
    public TraceObserverV2Adapter()
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean enableMetrics()
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onFilePublish(FilePublishEvent arg0)
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onFlowBegin()
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onFlowComplete()
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onFlowCreate(Session arg0)
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onFlowError(TaskEvent arg0)
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onProcessCreate(TaskProcessor arg0)
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onProcessTerminate(TaskProcessor arg0)
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onTaskCached(TaskEvent arg0)
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onTaskComplete(TaskEvent arg0)
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onTaskPending(TaskEvent arg0)
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onTaskStart(TaskEvent arg0)
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onTaskSubmit(TaskEvent arg0)
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onWorkflowOutput(WorkflowOutputEvent arg0)
    {
    }
}

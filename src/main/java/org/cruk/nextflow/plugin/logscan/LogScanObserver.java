package org.cruk.nextflow.plugin.logscan;

import java.nio.file.Path;
import java.nio.file.Paths;

import nextflow.Session;
import nextflow.processor.TaskProcessor;
import nextflow.trace.TraceObserverV2;
import nextflow.trace.TraceRecord;
import nextflow.trace.event.TaskEvent;
import nextflow.trace.event.FilePublishEvent;
import nextflow.trace.event.WorkflowOutputEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Observer that monitors running tasks and proactively creates exit code files.
 * <p>
 * Implements TraceObserverV2 to hook into Nextflow's task lifecycle and
 * coordinate the TaskMonitor background thread. The TaskMonitor scans task
 * log files while tasks are running and creates .exitcode files when tasks
 * are killed by external systems (e.g., SLURM memory limits).
 * </p>
 *
 * @author Richard Bowers
 */
public class LogScanObserver implements TraceObserverV2
{
    /**
     * Logger instance for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(LogScanObserver.class);

    /**
     * Configuration for log scanning.
     */
    private final LogScanConfig config;

    /**
     * The task monitor for proactive exit code creation.
     */
    private final LogScanTaskMonitor taskMonitor;

    /**
     * Constructs a new LogScanObserver.
     *
     * @param session the Nextflow session (not used, kept for compatibility)
     * @param config the log scan configuration
     */
    public LogScanObserver(Session session, LogScanConfig config)
    {
        this.config = config;
        this.taskMonitor = new LogScanTaskMonitor(config);
    }

    /**
     * Called when the workflow is created.
     *
     * @param session the Nextflow session
     */
    @Override
    public void onFlowCreate(Session session)
    {
        logger.debug("LogScan observer created");
    }

    /**
     * Called when the workflow begins execution.
     * <p>
     * Starts the task monitor thread for proactive exit code creation.
     * </p>
     */
    @Override
    public void onFlowBegin()
    {
        taskMonitor.start();

        logger.debug("LogScan observer - workflow begun");
    }

    /**
     * Called when the workflow completes.
     * <p>
     * Stops the task monitor thread.
     * </p>
     */
    @Override
    public void onFlowComplete()
    {
        taskMonitor.stop();

        logger.debug("LogScan observer completed");
    }

    /**
     * Called when a workflow encounters an error.
     *
     * @param event the task event
     */
    @Override
    public void onFlowError(TaskEvent event)
    {
        // Not needed
    }

    /**
     * Called when a process is created.
     * <p>
     * This hook is no longer used for afterScript injection. The TaskMonitor
     * background thread handles all log scanning and exit code creation.
     * </p>
     *
     * @param processor the task processor
     */
    @Override
    public void onProcessCreate(TaskProcessor processor)
    {
        // No action needed - TaskMonitor handles all log scanning
    }

    /**
     * Called when a process terminates.
     *
     * @param processor the task processor
     */
    @Override
    public void onProcessTerminate(TaskProcessor processor)
    {
        // Not needed
    }

    /**
     * Called when a task is pending.
     *
     * @param event the task event
     */
    @Override
    public void onTaskPending(TaskEvent event)
    {
        // Not needed
    }

    /**
     * Called when a task is submitted.
     * <p>
     * Registers the task with the monitor for proactive exit code creation.
     * </p>
     *
     * @param event the task event
     */
    @Override
    public void onTaskSubmit(TaskEvent event)
    {
        TraceRecord trace = event.getTrace();
        if (trace == null)
        {
            return;
        }

        // Get task ID and work directory
        Object taskIdObj = trace.get("task_id");
        Object workdirObj = trace.get("workdir");

        if (taskIdObj != null && workdirObj != null)
        {
            String taskId = taskIdObj.toString();
            Path workDir = Paths.get(workdirObj.toString());
            taskMonitor.registerTask(taskId, workDir);
        }
    }

    /**
     * Called when a task starts.
     *
     * @param event the task event
     */
    @Override
    public void onTaskStart(TaskEvent event)
    {
        // Not needed
    }

    /**
     * Called when a task completes (success or failure).
     * <p>
     * Unregisters the task from the monitor. The actual exit code modification
     * for externally-killed tasks happens in the TaskMonitor thread, which
     * proactively scans logs and creates .exitcode files before Nextflow
     * times out waiting for them.
     * </p>
     *
     * @param event the task event for the completed task
     */
    @Override
    public void onTaskComplete(TaskEvent event)
    {
        TraceRecord trace = event.getTrace();
        if (trace == null)
        {
            return;
        }

        // Unregister the task from monitoring
        Object taskIdObj = trace.get("task_id");
        if (taskIdObj != null)
        {
            taskMonitor.unregisterTask(taskIdObj.toString());
        }
    }

    /**
     * Called when a task is retrieved from cache.
     * <p>
     * Cached tasks don't need monitoring as they don't actually run.
     * </p>
     *
     * @param event the task event for the cached task
     */
    @Override
    public void onTaskCached(TaskEvent event)
    {
        // Cached tasks don't run, so no monitoring needed
    }

    /**
     * Called when a workflow output is published.
     *
     * @param event the workflow output event
     */
    @Override
    public void onWorkflowOutput(WorkflowOutputEvent event)
    {
        // Not needed
    }

    /**
     * Called when a file is published.
     *
     * @param event the file publish event
     */
    @Override
    public void onFilePublish(FilePublishEvent event)
    {
        // Not needed
    }

    /**
     * Indicates whether metrics are enabled.
     *
     * @return false
     */
    @Override
    public boolean enableMetrics()
    {
        return false;
    }
}

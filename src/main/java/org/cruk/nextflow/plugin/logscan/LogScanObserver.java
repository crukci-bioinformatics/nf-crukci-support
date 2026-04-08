package org.cruk.nextflow.plugin.logscan;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
 * Observer that monitors task completions and scans their log files.
 * <p>
 * Implements TraceObserverV2 to hook into Nextflow's task lifecycle
 * and perform log scanning when tasks complete.
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
     * The Nextflow session.
     */
    private final Session session;

    /**
     * Configuration for log scanning.
     */
    private final LogScanConfig config;

    /**
     * The log scanner instance.
     */
    private final LogScanner scanner;

    /**
     * The task monitor for proactive exit code creation.
     */
    private final TaskMonitor taskMonitor;

    /**
     * Constructs a new LogScanObserver.
     *
     * @param session the Nextflow session
     * @param config the log scan configuration
     */
    public LogScanObserver(Session session, LogScanConfig config)
    {
        this.session = session;
        this.config = config;
        this.scanner = new LogScanner(config);
        this.taskMonitor = new TaskMonitor(config);
    }

    /**
     * Called when the workflow is created.
     * <p>
     * Logs a message if verbose logging is enabled.
     * </p>
     *
     * @param session the Nextflow session
     */
    @Override
    public void onFlowCreate(Session session)
    {
        if (config.isVerbose())
        {
            logger.info("LogScan observer created");
        }
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

        if (config.isVerbose())
        {
            logger.info("LogScan observer - workflow begun");
        }
    }

    /**
     * Called when the workflow completes.
     * <p>
     * Stops the task monitor thread and logs a message if verbose logging is enabled.
     * </p>
     */
    @Override
    public void onFlowComplete()
    {
        taskMonitor.stop();

        if (config.isVerbose())
        {
            logger.info("LogScan observer completed");
        }
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
        if (!config.isEnabled())
        {
            return;
        }

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
     * Unregisters the task from the monitor and performs verbose logging if enabled.
     * This method runs AFTER the task has been finalized and error strategy applied.
     * </p>
     * <p>
     * The actual exit code modification for externally-killed tasks happens in the
     * TaskMonitor thread, which proactively scans logs and creates .exitcode files
     * before Nextflow times out waiting for them.
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

        // Only continue if verbose logging is enabled
        if (!config.isEnabled() || !config.isVerbose())
        {
            return;
        }

        // Check if we should scan based on task status
        Object exitObj = trace.get("exit");
        Integer exitStatus = null;
        if (exitObj instanceof Number exitCode)
        {
            exitStatus = exitCode.intValue();
        }

        boolean success = (exitStatus != null && exitStatus == 0);
        boolean shouldScan = (success && config.isScanOnSuccess()) || 
                             (!success && config.isScanOnFailure());

        if (!shouldScan)
        {
            return;
        }

        // Get the work directory and resolve the log file
        Object workdirObj = trace.get("workdir");
        if (workdirObj == null)
        {
            logger.debug("No workdir found in trace record");
            return;
        }

        Path workDirPath;
        if (workdirObj instanceof Path)
        {
            workDirPath = (Path) workdirObj;
        }
        else
        {
            workDirPath = Paths.get(workdirObj.toString());
        }

        Path logFile = workDirPath.resolve(".command.log");

        try
        {
            // Scan the log file for verbose reporting
            List<LogScanner.ScanMatch> matches = scanner.scanLogFile(logFile);

            if (!matches.isEmpty())
            {
                Object nameObj = trace.get("name");
                String taskName = nameObj != null ? nameObj.toString() : "unknown";

                logger.info("Task '{}' - LogScan found {} pattern match(es)",
                    taskName, matches.size());

                for (LogScanner.ScanMatch match : matches)
                {
                    logger.debug("  Pattern '{}' at line {}: {}",
                        match.getPattern().getName(),
                        match.getLineNumber(),
                        match.getMatchedText());
                }
            }
        }
        catch (Exception e)
        {
            logger.error("Error scanning log file: {}", logFile, e);
        }
    }

    /**
     * Called when a task is retrieved from cache.
     * <p>
     * This implementation optionally scans cached tasks based on configuration.
     * </p>
     *
     * @param event the task event for the cached task
     */
    @Override
    public void onTaskCached(TaskEvent event)
    {
        if (config.isScanOnSuccess())
        {
            onTaskComplete(event);
        }
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

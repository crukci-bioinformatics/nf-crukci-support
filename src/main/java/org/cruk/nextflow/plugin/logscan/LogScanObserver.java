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
 * @since 1.0.0
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
     */
    @Override
    public void onFlowBegin()
    {
        if (config.isVerbose())
        {
            logger.info("LogScan observer - workflow begun");
        }
    }

    /**
     * Called when the workflow completes.
     * <p>
     * Logs a message if verbose logging is enabled.
     * </p>
     */
    @Override
    public void onFlowComplete()
    {
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
     *
     * @param processor the task processor
     */
    @Override
    public void onProcessCreate(TaskProcessor processor)
    {
        // Not needed
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
     *
     * @param event the task event
     */
    @Override
    public void onTaskSubmit(TaskEvent event)
    {
        // Not needed
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
     * Scans the task's log file for configured patterns and
     * reports matches. If a memory limit pattern is found,
     * triggers a retry by throwing an exception.
     * </p>
     *
     * @param event the task event for the completed task
     */
    @Override
    public void onTaskComplete(TaskEvent event)
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

        // Check if we should scan based on task status
        Object exitObj = trace.get("exit");
        Integer exitStatus = null;
        if (exitObj instanceof Number exitCode)
        {
            exitStatus = exitCode.intValue();
        }

        boolean success = (exitStatus != null && exitStatus == 0);
        boolean shouldScan = false;

        if (success && config.isScanOnSuccess())
        {
            shouldScan = true;
        }
        else if (!success && config.isScanOnFailure())
        {
            shouldScan = true;
        }

        if (!shouldScan)
        {
            return;
        }

        // Get the work directory and resolve the log file
        Object workdirObj = trace.get("workdir");
        if (workdirObj == null)
        {
            if (config.isVerbose())
            {
                logger.debug("No workdir found in trace record");
            }
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
            // Scan the log file
            List<LogScanner.ScanMatch> matches = scanner.scanLogFile(logFile);

            // Report matches
            for (LogScanner.ScanMatch match : matches)
            {
                Object nameObj = trace.get("name");
                String taskName = nameObj != null ? nameObj.toString() : "unknown";

                logger.warn("Task '{}' - Pattern '{}' found at line {}: {}",
                    taskName, match.getPattern().getName(),
                    match.getLineNumber(), match.getMatchedText());

                // If this pattern has an exit code, update the trace record
                Integer patternExitCode = match.getPattern().getExitCode();
                if (patternExitCode != null)
                {
                    logger.warn("Task '{}' - Setting exit code to {} due to pattern match", 
                        taskName, patternExitCode);
                    trace.put("exit", patternExitCode);
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

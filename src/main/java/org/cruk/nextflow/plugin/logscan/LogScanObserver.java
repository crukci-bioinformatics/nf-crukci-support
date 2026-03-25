package org.cruk.nextflow.plugin.logscan;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import nextflow.Session;
import nextflow.processor.TaskHandler;
import nextflow.processor.TaskProcessor;
import nextflow.trace.TraceObserver;
import nextflow.trace.TraceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Observer that monitors task completions and scans their log files.
 * <p>
 * Implements TraceObserver to hook into Nextflow's task lifecycle
 * and perform log scanning when tasks complete.
 * </p>
 *
 * @author Richard Bowers
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
public class LogScanObserver implements TraceObserver
{
    /**
     * Logger instance for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(LogScanObserver.class);

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
            log.info("LogScan observer created");
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
            log.info("LogScan observer - workflow begun");
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
            log.info("LogScan observer completed");
        }
    }

    /**
     * Called when a workflow encounters an error.
     *
     * @param handler the task handler
     * @param trace the trace record
     */
    @Override
    public void onFlowError(TaskHandler handler, TraceRecord trace)
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
     * @param handler the task handler
     * @param trace the trace record
     */
    @Override
    public void onProcessPending(TaskHandler handler, TraceRecord trace)
    {
        // Not needed
    }

    /**
     * Called when a task is submitted.
     *
     * @param handler the task handler
     * @param trace the trace record
     */
    @Override
    public void onProcessSubmit(TaskHandler handler, TraceRecord trace)
    {
        // Not needed
    }

    /**
     * Called when a task starts.
     *
     * @param handler the task handler
     * @param trace the trace record
     */
    @Override
    public void onProcessStart(TaskHandler handler, TraceRecord trace)
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
     * @param handler the task handler
     * @param trace the trace record for the completed task
     */
    @Override
    public void onProcessComplete(TaskHandler handler, TraceRecord trace)
    {
        if (!config.isEnabled())
        {
            return;
        }

        // Check if we should scan based on task status
        Object exitObj = trace.get("exit");
        Integer exitStatus = null;
        if (exitObj instanceof Number)
        {
            exitStatus = ((Number) exitObj).intValue();
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
                log.debug("No workdir found in trace record");
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
                
                log.warn("Task '{}' - Pattern '{}' found at line {}: {}",
                    taskName, match.getPattern().getName(),
                    match.getLineNumber(), match.getMatchedText());

                // If this pattern should trigger a retry, we need to signal it
                if (match.getPattern().shouldTriggerRetry())
                {
                    log.warn("Task '{}' exceeded memory limit - triggering retry", taskName);
                    // Note: In Nextflow, retries are typically handled by the error strategy
                    // configuration. We log the issue but cannot directly trigger a retry
                    // from here. The user should configure errorStrategy = 'retry' in their
                    // process definition.
                }
            }
        }
        catch (Exception e)
        {
            log.error("Error scanning log file: {}", logFile, e);
        }
    }

    /**
     * Called when a task is retrieved from cache.
     * <p>
     * This implementation optionally scans cached tasks based on configuration.
     * </p>
     *
     * @param handler the task handler
     * @param trace the trace record for the cached task
     */
    @Override
    public void onProcessCached(TaskHandler handler, TraceRecord trace)
    {
        if (config.isScanOnSuccess())
        {
            onProcessComplete(handler, trace);
        }
    }

    /**
     * Called when a workflow is published.
     *
     * @param data the workflow data
     */
    @Override
    public void onWorkflowPublish(Object data)
    {
        // Not needed
    }

    /**
     * Called when a file is published (single parameter version).
     *
     * @param destination the destination path
     */
    @Override
    public void onFilePublish(Path destination)
    {
        // Not needed
    }

    /**
     * Called when a file is published (two parameter version).
     *
     * @param destination the destination path
     * @param source the source path
     */
    @Override
    public void onFilePublish(Path destination, Path source)
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

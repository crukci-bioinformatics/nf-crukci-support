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
     * This is the critical hook for implementing real-time log scanning.
     * We inject an afterScript that scans the task log and modifies the
     * .exitcode file BEFORE Nextflow reads it for error strategy evaluation.
     * </p>
     * <p>
     * This approach works because Nextflow's task wrapper (.command.run) writes
     * the task exit code to .exitcode, then runs afterScript, then reads .exitcode
     * to determine the final exit status. By scanning the log in afterScript and
     * overwriting .exitcode, we can change the exit code that Nextflow's error
     * strategy will process.
     * </p>
     *
     * @param processor the task processor
     */
    @Override
    public void onProcessCreate(TaskProcessor processor)
    {
        if (!config.isEnabled())
        {
            return;
        }

        try
        {
            injectLogScanAfterScript(processor);

            if (config.isVerbose())
            {
                logger.info("Injected log scan script into process: {}", processor.getName());
            }
        }
        catch (Exception e)
        {
            logger.error("Failed to inject log scan script for process: {}", processor.getName(), e);
        }
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

        // Rest is just verbose logging
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

    /**
     * Injects an afterScript that scans the log and modifies the exit code.
     * <p>
     * The injected script runs after the main task script completes but BEFORE
     * the Nextflow wrapper finalizes the task. It scans .command.log for configured
     * patterns and, if a pattern with an exit code is found, overwrites the
     * .exitcode file that Nextflow will read to determine the final task status.
     * </p>
     *
     * @param processor the task processor to modify
     */
    private void injectLogScanAfterScript(TaskProcessor processor)
    {
        try
        {
            // Get the process config
            var processConfig = processor.getConfig();
            if (processConfig == null)
            {
                if (config.isVerbose())
                {
                    logger.debug("No process config available for: {}", processor.getName());
                }
                return;
            }

            String scanScript = buildLogScanScript();

            // Get existing afterScript
            Object existingAfterScript = processConfig.get("afterScript");
            String combinedScript;

            if (existingAfterScript != null && !existingAfterScript.toString().trim().isEmpty())
            {
                // Prepend our script so it runs first
                combinedScript = scanScript + "\n\n" + existingAfterScript.toString();
            }
            else
            {
                combinedScript = scanScript;
            }

            // Set the combined afterScript
            processConfig.put("afterScript", combinedScript);
        }
        catch (Exception e)
        {
            logger.error("Failed to inject afterScript: {}", e.getMessage(), e);
        }
    }

    /**
     * Builds the Bash script that scans the log and modifies the exit code.
     * <p>
     * The script:
     * 1. Reads the original exit code from .exitcode file
     * 2. Scans .command.log line by line for configured patterns
     * 3. If a pattern with an exit code is found, overwrites .exitcode
     * 4. Otherwise, leaves .exitcode unchanged
     * </p>
     * <p>
     * The Nextflow wrapper (.command.run) reads .exitcode after running
     * afterScript, so modifications made here will affect the final task
     * exit status that Nextflow's error strategy evaluates.
     * </p>
     *
     * @return the Bash script as a string
     */
    private String buildLogScanScript()
    {
        StringBuilder script = new StringBuilder();

        script.append("# ==============================================================================\n");
        script.append("# LogScan Plugin: Log Pattern Scanner and Exit Code Modifier\n");
        script.append("# ==============================================================================\n\n");

        // Read the original exit code
        script.append("# Read original exit code from .exitcode file\n");
        script.append("if [ -f .exitcode ]; then\n");
        script.append("  ORIGINAL_EXIT=$(cat .exitcode)\n");
        script.append("else\n");
        script.append("  ORIGINAL_EXIT=$?\n");
        script.append("fi\n\n");

        script.append("LOG_FILE=\".command.log\"\n");
        script.append("EXITCODE_FILE=\".exitcode\"\n\n");

        // Check if we should scan based on configuration
        script.append("# Determine if we should scan based on configuration\n");
        script.append("SHOULD_SCAN=0\n");

        if (config.isScanOnFailure())
        {
            script.append("[ \"$ORIGINAL_EXIT\" -ne 0 ] && SHOULD_SCAN=1\n");
        }

        if (config.isScanOnSuccess())
        {
            script.append("[ \"$ORIGINAL_EXIT\" -eq 0 ] && SHOULD_SCAN=1\n");
        }

        script.append("\n");
        script.append("if [ $SHOULD_SCAN -eq 0 ]; then\n");
        script.append("  exit 0\n");
        script.append("fi\n\n");

        // Check if log file exists
        script.append("# Check if log file exists\n");
        script.append("if [ ! -f \"$LOG_FILE\" ]; then\n");
        script.append("  exit 0\n");
        script.append("fi\n\n");

        // Scan for patterns
        script.append("# Scan log file for configured patterns\n");
        script.append("LINE_NUM=0\n");
        script.append("MAX_LINES=").append(config.getMaxLinesToScan()).append("\n");
        script.append("PATTERN_MATCHED=0\n");
        script.append("NEW_EXIT_CODE=\"\"\n\n");

        script.append("while IFS= read -r line || [ -n \"$line\" ]; do\n");
        script.append("  LINE_NUM=$((LINE_NUM + 1))\n");
        script.append("  \n");
        script.append("  # Check line limit\n");
        script.append("  if [ $MAX_LINES -gt 0 ] && [ $LINE_NUM -gt $MAX_LINES ]; then\n");
        script.append("    break\n");
        script.append("  fi\n\n");

        // Generate pattern checks
        List<LogScanConfig.ScanPattern> patterns = config.getPatterns();
        for (int i = 0; i < patterns.size(); i++)
        {
            LogScanConfig.ScanPattern pattern = patterns.get(i);
            Integer exitCode = pattern.getExitCode();

            if (exitCode != null)
            {
                // Get the regex string from the compiled Pattern
                String regex = pattern.getPattern().pattern();

                // Check if pattern is case-insensitive
                boolean caseSensitive = (pattern.getPattern().flags() & java.util.regex.Pattern.CASE_INSENSITIVE) == 0;
                String grepFlags = caseSensitive ? "E" : "iE";

                script.append("  # Pattern ").append(i + 1).append(": ").append(pattern.getName()).append("\n");
                script.append("  if [ $PATTERN_MATCHED -eq 0 ]; then\n");
                script.append("    if echo \"$line\" | grep -q").append(grepFlags)
                      .append(" '").append(escapeForBash(regex)).append("'; then\n");
                script.append("      echo \"[LogScan] Pattern '").append(escapeForBash(pattern.getName()))
                      .append("' matched at line $LINE_NUM\" >&2\n");
                script.append("      echo \"[LogScan] Original exit: $ORIGINAL_EXIT, New exit: ")
                      .append(exitCode).append("\" >&2\n");
                script.append("      PATTERN_MATCHED=1\n");
                script.append("      NEW_EXIT_CODE=\"").append(exitCode).append("\"\n");
                script.append("    fi\n");
                script.append("  fi\n\n");
            }
        }

        script.append("done < \"$LOG_FILE\"\n\n");

        // Update exit code file if pattern matched
        script.append("# Update .exitcode file if a pattern was matched\n");
        script.append("if [ $PATTERN_MATCHED -eq 1 ]; then\n");
        script.append("  echo \"$NEW_EXIT_CODE\" > \"$EXITCODE_FILE\"\n");
        script.append("  echo \"[LogScan] Updated $EXITCODE_FILE with new exit code\" >&2\n");
        script.append("fi\n\n");

        // Always exit 0 from afterScript
        script.append("# Exit successfully - the .exitcode file will be read by Nextflow\n");
        script.append("exit 0\n");

        return script.toString();
    }

    /**
     * Escapes a string for use in a Bash single-quoted string.
     * <p>
     * In Bash single quotes, the single quote character needs special handling.
     * We escape it by ending the single-quoted string, adding an escaped single
     * quote, and starting a new single-quoted string: 'can'\''t'
     * </p>
     *
     * @param str the string to escape
     * @return the escaped string safe for Bash single quotes
     */
    private String escapeForBash(String str)
    {
        return str.replace("'", "'\"'\"'");
    }
}

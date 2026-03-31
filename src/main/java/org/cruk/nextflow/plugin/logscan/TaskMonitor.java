package org.cruk.nextflow.plugin.logscan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitors running tasks and proactively creates exit code files when
 * tasks are killed by external systems (e.g., SLURM memory limits).
 * <p>
 * This class addresses the problem where tasks killed by the scheduler
 * don't have time to write .exitcode files, causing Nextflow to report
 * "terminated by external system" errors.
 * </p>
 * <p>
 * The monitor runs a background thread that periodically checks registered
 * task work directories. When a task's .command.log exists but .exitcode
 * doesn't, it scans the log for error patterns and creates an appropriate
 * .exitcode file before Nextflow times out waiting for it.
 * </p>
 *
 * @author Richard Bowers
 * @since 1.0.0
 */
public class TaskMonitor
{
    /**
     * Logger instance for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(TaskMonitor.class);

    /**
     * Configuration for log scanning.
     */
    private final LogScanConfig config;

    /**
     * The log scanner instance.
     */
    private final LogScanner scanner;

    /**
     * Map of task IDs to their work directories.
     * Key: task ID (from TraceRecord), Value: work directory path
     */
    private final Map<String, Path> activeTasks;

    /**
     * Executor service for the monitoring thread.
     */
    private final ScheduledExecutorService executor;

    /**
     * Whether the monitor is currently running.
     */
    private volatile boolean running;

    /**
     * How often to check tasks (in seconds).
     */
    private static final long CHECK_INTERVAL_SECONDS = 5;

    /**
     * Constructs a new TaskMonitor.
     *
     * @param config the log scan configuration
     */
    public TaskMonitor(LogScanConfig config)
    {
        this.config = config;
        this.scanner = new LogScanner(config);
        this.activeTasks = new ConcurrentHashMap<>();
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LogScan-TaskMonitor");
            t.setDaemon(true);
            return t;
        });
        this.running = false;
    }

    /**
     * Starts the monitoring thread.
     */
    public void start()
    {
        if (running)
        {
            return;
        }

        running = true;
        executor.scheduleAtFixedRate(
            this::checkAllTasks,
            CHECK_INTERVAL_SECONDS,
            CHECK_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );

        if (config.isVerbose())
        {
            logger.info("TaskMonitor started (check interval: {}s)", CHECK_INTERVAL_SECONDS);
        }
    }

    /**
     * Stops the monitoring thread.
     */
    public void stop()
    {
        if (!running)
        {
            return;
        }

        running = false;
        executor.shutdown();
        try
        {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS))
            {
                executor.shutdownNow();
            }
        }
        catch (InterruptedException e)
        {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (config.isVerbose())
        {
            logger.info("TaskMonitor stopped");
        }
    }

    /**
     * Registers a task for monitoring.
     *
     * @param taskId the task ID
     * @param workDir the task's work directory
     */
    public void registerTask(String taskId, Path workDir)
    {
        if (taskId != null && workDir != null)
        {
            activeTasks.put(taskId, workDir);
            if (config.isVerbose())
            {
                logger.debug("Registered task {} for monitoring: {}", taskId, workDir);
            }
        }
    }

    /**
     * Unregisters a task from monitoring.
     *
     * @param taskId the task ID
     */
    public void unregisterTask(String taskId)
    {
        if (taskId != null)
        {
            activeTasks.remove(taskId);
            if (config.isVerbose())
            {
                logger.debug("Unregistered task {}", taskId);
            }
        }
    }

    /**
     * Checks all registered tasks for missing exit codes.
     */
    private void checkAllTasks()
    {
        if (!running)
        {
            return;
        }

        for (Map.Entry<String, Path> entry : activeTasks.entrySet())
        {
            String taskId = entry.getKey();
            Path workDir = entry.getValue();

            try
            {
                checkTask(taskId, workDir);
            }
            catch (Exception e)
            {
                logger.debug("Error checking task {}: {}", taskId, e.getMessage());
            }
        }
    }

    /**
     * Checks a single task for missing exit code.
     * <p>
     * If the task's .command.log exists but .exitcode doesn't, and the
     * log contains error patterns, creates an appropriate .exitcode file.
     * </p>
     *
     * @param taskId the task ID
     * @param workDir the task's work directory
     */
    private void checkTask(String taskId, Path workDir)
    {
        Path logFile = workDir.resolve(".command.log");
        Path exitFile = workDir.resolve(".exitcode");

        // Check if we need to intervene
        if (!Files.exists(logFile) || Files.exists(exitFile))
        {
            // Log doesn't exist yet, or exit code already written
            return;
        }

        // Check if the log file has been modified recently (task might be running)
        try
        {
            long lastModified = Files.getLastModifiedTime(logFile).toMillis();
            long now = System.currentTimeMillis();
            long ageSeconds = (now - lastModified) / 1000;

            // Only intervene if log hasn't been modified for at least 2 seconds
            // (task has stopped writing to log)
            if (ageSeconds < 2)
            {
                return;
            }
        }
        catch (IOException e)
        {
            return;
        }

        // Scan the log file for patterns
        try
        {
            List<LogScanner.ScanMatch> matches = scanner.scanLogFile(logFile);
            if (matches.isEmpty())
            {
                return;
            }

            // Found pattern matches - create exit code file
            LogScanner.ScanMatch firstMatch = matches.get(0);
            LogScanConfig.ScanPattern matchedPattern = firstMatch.getPattern();
            Integer exitCodeObj = matchedPattern.getExitCode();
            int exitCode = (exitCodeObj != null) ? exitCodeObj : 1;

            createExitCodeFile(exitFile, exitCode);

            logger.warn(
                "Task {} killed by external system - pattern '{}' detected in log, created .exitcode with status {}",
                taskId,
                matchedPattern.getName(),
                exitCode
            );

            // Unregister this task - we've handled it
            unregisterTask(taskId);
        }
        catch (IOException e)
        {
            logger.debug("Failed to scan log for task {}: {}", taskId, e.getMessage());
        }
    }

    /**
     * Creates an exit code file with the specified exit code.
     *
     * @param exitFile the path to the .exitcode file
     * @param exitCode the exit code to write
     * @throws IOException if the file cannot be created
     */
    private void createExitCodeFile(Path exitFile, int exitCode) throws IOException
    {
        Files.writeString(
            exitFile,
            String.valueOf(exitCode),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        if (config.isVerbose())
        {
            logger.info("Created {} with exit code {}", exitFile, exitCode);
        }
    }
}

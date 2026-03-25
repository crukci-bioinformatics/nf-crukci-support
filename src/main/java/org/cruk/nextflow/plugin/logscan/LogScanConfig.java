package org.cruk.nextflow.plugin.logscan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration holder for the log scan plugin.
 * <p>
 * Reads configuration from the 'logScan' scope in nextflow.config
 * and provides structured access to plugin settings.
 * </p>
 *
 * @author Richard Bowers
 * @since 1.0.0
 */
public class LogScanConfig
{
    /**
     * Logger instance for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(LogScanConfig.class);

    /**
     * Whether log scanning is enabled.
     */
    private final boolean enabled;

    /**
     * Whether to scan logs for successful tasks.
     */
    private final boolean scanOnSuccess;

    /**
     * Whether to scan logs for failed tasks.
     */
    private final boolean scanOnFailure;

    /**
     * Maximum number of lines to scan (0 = unlimited).
     */
    private final int maxLinesToScan;

    /**
     * Whether to enable verbose logging.
     */
    private final boolean verbose;

    /**
     * List of compiled regex patterns to search for.
     */
    private final List<ScanPattern> patterns;

    /**
     * Represents a pattern to scan for in log files.
     */
    public static class ScanPattern
    {
        /**
         * The compiled regex pattern.
         */
        private final Pattern pattern;

        /**
         * The name/description of this pattern.
         */
        private final String name;

        /**
         * Whether the pattern should trigger a retry.
         */
        private final boolean triggerRetry;

        /**
         * Constructs a new ScanPattern.
         *
         * @param pattern the compiled regex pattern
         * @param name the name/description of this pattern
         * @param triggerRetry whether this pattern should trigger a retry
         */
        public ScanPattern(Pattern pattern, String name, boolean triggerRetry)
        {
            this.pattern = pattern;
            this.name = name;
            this.triggerRetry = triggerRetry;
        }

        /**
         * Gets the compiled regex pattern.
         *
         * @return the pattern
         */
        public Pattern getPattern()
        {
            return pattern;
        }

        /**
         * Gets the name/description of this pattern.
         *
         * @return the pattern name
         */
        public String getName()
        {
            return name;
        }

        /**
         * Checks if this pattern should trigger a retry.
         *
         * @return true if a retry should be triggered
         */
        public boolean shouldTriggerRetry()
        {
            return triggerRetry;
        }
    }

    /**
     * Constructs a new LogScanConfig from a configuration map.
     *
     * @param config the configuration map from nextflow.config
     */
    @SuppressWarnings("unchecked")
    public LogScanConfig(Map<String, Object> config)
    {
        if (config == null)
        {
            config = Map.of();
        }

        this.enabled = getBooleanValue(config, "enabled", true);
        this.scanOnSuccess = getBooleanValue(config, "scanOnSuccess", false);
        this.scanOnFailure = getBooleanValue(config, "scanOnFailure", true);
        this.maxLinesToScan = getIntValue(config, "maxLinesToScan", 10000);
        this.verbose = getBooleanValue(config, "verbose", false);

        // Load patterns
        this.patterns = new ArrayList<>();
        Object patternsObj = config.get("patterns");

        if (patternsObj instanceof List)
        {
            List<?> patternsList = (List<?>) patternsObj;
            for (Object patternObj : patternsList)
            {
                if (patternObj instanceof String patternStr)
                {
                    // Simple string pattern
                    boolean isMemoryPattern = patternStr.contains("memory limit");
                    patterns.add(new ScanPattern(
                        Pattern.compile(patternStr),
                        patternStr,
                        isMemoryPattern
                    ));
                }
                else if (patternObj instanceof Map)
                {
                    // Map with pattern details
                    Map<String, Object> patternMap = (Map<String, Object>) patternObj;
                    String patternStr = (String) patternMap.get("pattern");
                    String name = (String) patternMap.getOrDefault("name", patternStr);
                    boolean caseSensitive = getBooleanValue(patternMap, "caseSensitive", true);
                    boolean triggerRetry = getBooleanValue(patternMap, "triggerRetry",
                        patternStr != null && patternStr.contains("memory limit"));

                    if (patternStr != null)
                    {
                        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                        patterns.add(new ScanPattern(
                            Pattern.compile(patternStr, flags),
                            name,
                            triggerRetry
                        ));
                    }
                }
            }
        }

        // Add default memory limit pattern if no patterns configured
        if (patterns.isEmpty())
        {
            patterns.add(new ScanPattern(
                Pattern.compile("Exceeded job memory limit"),
                "Memory Limit Exceeded",
                true
            ));
        }

        if (verbose)
        {
            log.info("LogScan config: enabled={}, scanOnSuccess={}, scanOnFailure={}, patterns={}",
                enabled, scanOnSuccess, scanOnFailure, patterns.size());
        }
    }

    /**
     * Gets a boolean value from a map with a default.
     *
     * @param map the map to read from
     * @param key the key to look up
     * @param defaultValue the default value if not found
     * @return the boolean value
     */
    private boolean getBooleanValue(Map<String, Object> map, String key, boolean defaultValue)
    {
        Object value = map.get(key);
        if (value instanceof Boolean)
        {
            return (Boolean) value;
        }
        return defaultValue;
    }

    /**
     * Gets an integer value from a map with a default.
     *
     * @param map the map to read from
     * @param key the key to look up
     * @param defaultValue the default value if not found
     * @return the integer value
     */
    private int getIntValue(Map<String, Object> map, String key, int defaultValue)
    {
        Object value = map.get(key);
        if (value instanceof Number)
        {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
     * Checks if log scanning is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * Checks if scanning should happen for successful tasks.
     *
     * @return true if successful tasks should be scanned
     */
    public boolean isScanOnSuccess()
    {
        return scanOnSuccess;
    }

    /**
     * Checks if scanning should happen for failed tasks.
     *
     * @return true if failed tasks should be scanned
     */
    public boolean isScanOnFailure()
    {
        return scanOnFailure;
    }

    /**
     * Gets the maximum number of lines to scan.
     *
     * @return the maximum lines (0 = unlimited)
     */
    public int getMaxLinesToScan()
    {
        return maxLinesToScan;
    }

    /**
     * Checks if verbose logging is enabled.
     *
     * @return true if verbose logging is enabled
     */
    public boolean isVerbose()
    {
        return verbose;
    }

    /**
     * Gets the list of patterns to scan for.
     *
     * @return the list of scan patterns
     */
    public List<ScanPattern> getPatterns()
    {
        return patterns;
    }
}

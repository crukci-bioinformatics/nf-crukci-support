package org.cruk.nextflow.plugin.crukci;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nextflow.Session;
import nextflow.util.MemoryUnit;

/**
 * Configuration holder for the CRUK-CI plugin.
 * <p>
 * Reads configuration from the 'crukci' scope in nextflow.config
 * and provides structured access to plugin settings.
 * </p>
 *
 * @author Richard Bowers
 */
public class CRUKCIConfig
{
    /**
     * One megabyte, for legibility of setting up the other constants.
     */
    private static final long MEGA = 1 << 20;

    /**
     * The smallest allowed margin for JVM overheads: 32MB.
     */
    public static final MemoryUnit MINIMUM_JAVA_OVERHEAD = MemoryUnit.of(32L * MEGA);

    /**
     * The default margin for JVM overheads: 64MB.
     */
    public static final String DEFAULT_JAVA_OVERHEAD = "64M";

    /**
     * The smallest allowed meta space size (class definitions etc).
     */
    public static final MemoryUnit MINIMUM_JAVA_METASPACE = MemoryUnit.of(64L * MEGA);

    /**
     * The default meta space size.
     */
    public static final String DEFAULT_JAVA_METASPACE = "128M";

    /**
     * The smallest allowed Java heap size to be functional: 16MB.
     */
    public static final MemoryUnit MINIMUM_JAVA_HEAP = MemoryUnit.of(16L * MEGA);

    /**
     * The default number of log lines to scan to find error patterns.
     */
    public static final int DEFAULT_LINES_TO_SCAN = 10000;

    /**
     * Logger instance for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(CRUKCIConfig.class);

    /**
     * Flag to prevent the user getting messages about too small memory allocations
     * multiple times.
     */
    private static boolean limitsWarned = false;

    /**
     * Memory reserved for the JVM overhead.
     */
    public final MemoryUnit javaOverhead;

    /**
     * Memory reserved for the JVM metaspace overhead.
     */
    public final MemoryUnit javaMetaspace;

    /**
     * Maximum number of lines to scan (&le;0 is unlimited).
     */
    public final int maxLinesToScan;

    /**
     * List of compiled regex patterns to search for.
     */
    public final List<ScanPattern> patterns;

    /**
     * Represents a pattern to scan for in log files.
     */
    public static class ScanPattern
    {
        /**
         * The compiled regex pattern.
         */
        public final Pattern pattern;

        /**
         * The name/description of this pattern.
         */
        public final String name;

        /**
         * The exit code to set when this pattern is matched.
         * A value of null means no exit code override.
         */
        public final Integer exitCode;

        /**
         * Constructs a new ScanPattern.
         *
         * @param pattern the compiled regex pattern
         * @param name the name/description of this pattern
         * @param exitCode the exit code to set when matched (null = no override)
         */
        public ScanPattern(Pattern pattern, String name, Integer exitCode)
        {
            this.pattern = pattern;
            this.name = name;
            this.exitCode = exitCode;
        }
    }

    /**
     * Constructs a new LogScanConfig from a configuration map in the Nextflow session.
     *
     * @param session The Nextflow session.
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    public CRUKCIConfig(Session session)
    {
        Map<String, Object> configMap = Collections.emptyMap();

        Object config = session.getConfig().get("crukci");
        if (config instanceof Map map)
        {
            configMap = map;
        }

        MemoryUnit overhead = MemoryUnit.of(configMap.getOrDefault("javaOverhead", DEFAULT_JAVA_OVERHEAD).toString());
        if (overhead.compareTo(MINIMUM_JAVA_OVERHEAD) < 0)
        {
            if (!limitsWarned)
            {
                logger.warn("javaOverhead is set to {}, which is too small. Setting to the minimum of {}.", overhead, MINIMUM_JAVA_OVERHEAD);
            }
            overhead = MINIMUM_JAVA_OVERHEAD;
        }
        this.javaOverhead = overhead;

        MemoryUnit metaspace = MemoryUnit.of(configMap.getOrDefault("javaMetaspace", DEFAULT_JAVA_METASPACE).toString());
        if (metaspace.compareTo(MINIMUM_JAVA_METASPACE) < 0)
        {
            if (!limitsWarned)
            {
                logger.warn("javaMetaspace is set to {}, which is too small. Setting to the minimum of {}.", metaspace, MINIMUM_JAVA_METASPACE);
            }
            metaspace = MINIMUM_JAVA_METASPACE;
        }
        this.javaMetaspace = metaspace;

        this.maxLinesToScan = getIntValue(configMap, "maxLinesToScan", 10000);

        // Load patterns
        Object patternsObj = configMap.get("patterns");

        List<ScanPattern> patterns = new ArrayList<>();

        if (patternsObj instanceof Collection<?> patternsList)
        {
            for (Object patternObj : patternsList)
            {
                if (patternObj instanceof String patternStr)
                {
                    // Simple string pattern
                    boolean isMemoryPattern = patternStr.contains("memory limit");
                    Integer exitCode = isMemoryPattern ? 137 : null;
                    patterns.add(new ScanPattern(
                        Pattern.compile(patternStr),
                        patternStr,
                        exitCode
                    ));
                }
                else if (patternObj instanceof Map)
                {
                    // Map with pattern details
                    Map<String, Object> patternMap = (Map<String, Object>) patternObj;
                    String patternStr = (String) patternMap.get("pattern");
                    String name = (String) patternMap.getOrDefault("name", patternStr);
                    boolean caseSensitive = getBooleanValue(patternMap, "caseSensitive", true);
                    boolean isMemoryPattern = patternStr != null && patternStr.contains("memory limit");
                    Integer exitCode = getIntegerValue(patternMap, "exitCode", isMemoryPattern ? 137 : null);

                    if (patternStr != null)
                    {
                        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                        patterns.add(new ScanPattern(
                            Pattern.compile(patternStr, flags),
                            name,
                            exitCode
                        ));
                    }
                }
            }
        }

        // Add the default patterns to the configured list.
        patterns.add(new ScanPattern(
            Pattern.compile("Exceeded job memory limit"),
            "Memory Limit Exceeded",
            137
        ));
        patterns.add(new ScanPattern(
            Pattern.compile(Pattern.quote(OutOfMemoryError.class.getName())),
            "Java Heap Exhausted",
            137
        ));

        this.patterns = Collections.unmodifiableList(patterns);
        limitsWarned = true;
    }

    /**
     * Gets a boolean value from a map with a default.
     *
     * @param map the map to read from
     * @param key the key to look up
     * @param defaultValue the default value if not found
     *
     * @return The value from the map, or the default value.
     */
    private boolean getBooleanValue(Map<String, Object> map, String key, boolean defaultValue)
    {
        Object value = map.get(key);
        if (value instanceof Boolean bool)
        {
            return bool.booleanValue();
        }
        return defaultValue;
    }

    /**
     * Gets an integer value from a map with a default.
     *
     * @param map the map to read from
     * @param key the key to look up
     * @param defaultValue the default value if not found
     *
     * @return The value from the map, or the default value.
     */
    private int getIntValue(Map<String, Object> map, String key, int defaultValue)
    {
        Object value = map.get(key);
        if (value instanceof Number num)
        {
            return num.intValue();
        }
        return defaultValue;
    }

    /**
     * Gets an Integer value from a map with a default (nullable).
     *
     * @param map the map to read from
     * @param key the key to look up
     * @param defaultValue the default value if not found
     *
     * @return The value from the map, or the default value.
     */
    private Integer getIntegerValue(Map<String, Object> map, String key, Integer defaultValue)
    {
        Object value = map.get(key);
        if (value instanceof Number num)
        {
            return num.intValue();
        }
        return defaultValue;
    }
}

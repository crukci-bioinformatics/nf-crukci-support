package org.cruk.nextflow.plugin.logscan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for LogScanConfig.
 *
 * @author Richard Bowers
 */
class LogScanConfigTest
{
    /**
     * Tests default configuration values.
     */
    @Test
    void testDefaultConfig()
    {
        LogScanConfig config = new LogScanConfig(null);

        assertEquals(10000, config.getMaxLinesToScan());

        // Should have default memory limit pattern
        assertEquals(2, config.getPatterns().size());

        assertEquals("Memory Limit Exceeded", config.getPatterns().get(0).getName());
        assertEquals(137, config.getPatterns().get(0).getExitCode());

        assertEquals("Java Heap Exhausted", config.getPatterns().get(1).getName());
        assertEquals(137, config.getPatterns().get(1).getExitCode());
    }

    /**
     * Tests custom configuration values.
     */
    @Test
    void testCustomConfig()
    {
        Map<String, Object> configMap = Map.of(
            "maxLinesToScan", 5000
        );

        LogScanConfig config = new LogScanConfig(configMap);

        assertEquals(5000, config.getMaxLinesToScan());
    }

    /**
     * Tests pattern loading from strings.
     */
    @Test
    void testStringPatterns()
    {
        Map<String, Object> configMap = Map.of(
            "patterns", List.of("ERROR", "WARNING")
        );

        LogScanConfig config = new LogScanConfig(configMap);

        assertEquals(2, config.getPatterns().size());
        assertEquals("ERROR", config.getPatterns().get(0).getName());
        assertEquals("WARNING", config.getPatterns().get(1).getName());
        assertNull(config.getPatterns().get(0).getExitCode());
        assertNull(config.getPatterns().get(1).getExitCode());
    }

    /**
     * Tests pattern loading from maps.
     */
    @Test
    void testMapPatterns()
    {
        Map<String, Object> configMap = Map.of(
            "patterns", List.of(
                Map.of("pattern", "ERROR", "name", "Error Pattern", "caseSensitive", true),
                Map.of("pattern", "warning", "name", "Warning Pattern", "caseSensitive", false),
                Map.of("pattern", "memory limit", "name", "Memory", "exitCode", 137)
            )
        );

        LogScanConfig config = new LogScanConfig(configMap);

        assertEquals(3, config.getPatterns().size());
        assertEquals("Error Pattern", config.getPatterns().get(0).getName());
        assertEquals("Warning Pattern", config.getPatterns().get(1).getName());
        assertEquals("Memory", config.getPatterns().get(2).getName());
        assertNull(config.getPatterns().get(0).getExitCode());
        assertNull(config.getPatterns().get(1).getExitCode());
        assertEquals(137, config.getPatterns().get(2).getExitCode());
    }

    /**
     * Tests automatic detection of memory limit patterns.
     */
    @Test
    void testMemoryLimitAutoDetection()
    {
        Map<String, Object> configMap = Map.of(
            "patterns", List.of("Exceeded job memory limit", "ERROR")
        );

        LogScanConfig config = new LogScanConfig(configMap);

        assertEquals(2, config.getPatterns().size());
        assertEquals(137, config.getPatterns().get(0).getExitCode());
        assertNull(config.getPatterns().get(1).getExitCode());
    }
}

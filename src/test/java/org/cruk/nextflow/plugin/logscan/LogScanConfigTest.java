package org.cruk.nextflow.plugin.logscan;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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

        assertTrue(config.isEnabled());
        assertFalse(config.isScanOnSuccess());
        assertTrue(config.isScanOnFailure());
        assertEquals(10000, config.getMaxLinesToScan());
        assertFalse(config.isVerbose());

        // Should have default memory limit pattern
        assertEquals(1, config.getPatterns().size());
        assertEquals("Memory Limit Exceeded", config.getPatterns().get(0).getName());
        assertEquals(137, config.getPatterns().get(0).getExitCode());
    }

    /**
     * Tests custom configuration values.
     */
    @Test
    void testCustomConfig()
    {
        Map<String, Object> configMap = Map.of(
            "enabled", false,
            "scanOnSuccess", true,
            "scanOnFailure", false,
            "maxLinesToScan", 5000,
            "verbose", true
        );

        LogScanConfig config = new LogScanConfig(configMap);

        assertFalse(config.isEnabled());
        assertTrue(config.isScanOnSuccess());
        assertFalse(config.isScanOnFailure());
        assertEquals(5000, config.getMaxLinesToScan());
        assertTrue(config.isVerbose());
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

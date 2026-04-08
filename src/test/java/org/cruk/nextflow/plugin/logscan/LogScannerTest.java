package org.cruk.nextflow.plugin.logscan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LogScanner.
 *
 * @author Richard Bowers
 */
class LogScannerTest
{
    /**
     * Temporary directory for test files.
     */
    @TempDir
    Path tempDir;

    /**
     * Tests scanning a log file with matches.
     */
    @Test
    void testScanLogFileWithMatches() throws IOException
    {
        // Create a test log file
        Path logFile = tempDir.resolve("test.log");
        Files.writeString(logFile,
            "Line 1: Normal log entry\n" +
            "Line 2: ERROR: Something went wrong\n" +
            "Line 3: Another normal entry\n" +
            "Line 4: WARNING: Be careful\n" +
            "Line 5: Final entry\n"
        );

        // Configure scanner with patterns
        Map<String, Object> configMap = Map.of(
            "patterns", List.of("ERROR", "WARNING")
        );
        LogScanConfig config = new LogScanConfig(configMap);
        LogScanner scanner = new LogScanner(config);

        // Scan the file
        List<LogScanner.ScanMatch> matches = scanner.scanLogFile(logFile);

        // Verify matches
        assertEquals(2, matches.size());

        assertEquals("ERROR", matches.get(0).getPattern().getName());
        assertEquals(2, matches.get(0).getLineNumber());
        assertEquals("ERROR", matches.get(0).getMatchedText());

        assertEquals("WARNING", matches.get(1).getPattern().getName());
        assertEquals(4, matches.get(1).getLineNumber());
        assertEquals("WARNING", matches.get(1).getMatchedText());
    }

    /**
     * Tests scanning a non-existent log file.
     */
    @Test
    void testScanNonExistentFile() throws IOException
    {
        Path logFile = tempDir.resolve("nonexistent.log");

        LogScanConfig config = new LogScanConfig(null);
        LogScanner scanner = new LogScanner(config);

        List<LogScanner.ScanMatch> matches = scanner.scanLogFile(logFile);

        assertTrue(matches.isEmpty());
    }

    /**
     * Tests scanning with max lines limit.
     */
    @Test
    void testMaxLinesToScan() throws IOException
    {
        // Create a test log file with many lines
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 100; i++)
        {
            content.append("Line ").append(i).append(": ERROR\n");
        }

        Path logFile = tempDir.resolve("test.log");
        Files.writeString(logFile, content.toString());

        // Configure scanner with max lines
        Map<String, Object> configMap = Map.of(
            "patterns", List.of("ERROR"),
            "maxLinesToScan", 10
        );
        LogScanConfig config = new LogScanConfig(configMap);
        LogScanner scanner = new LogScanner(config);

        // Scan the file
        List<LogScanner.ScanMatch> matches = scanner.scanLogFile(logFile);

        // Should only find matches in first 10 lines
        assertEquals(10, matches.size());
        assertEquals(10, matches.get(9).getLineNumber());
    }

    /**
     * Tests case-insensitive pattern matching.
     */
    @Test
    void testCaseInsensitiveMatching() throws IOException
    {
        Path logFile = tempDir.resolve("test.log");
        Files.writeString(logFile,
            "Line 1: error in lowercase\n" +
            "Line 2: ERROR in uppercase\n" +
            "Line 3: Error in mixed case\n"
        );

        Map<String, Object> configMap = Map.of(
            "patterns", List.of(
                Map.of("pattern", "error", "caseSensitive", false)
            )
        );
        LogScanConfig config = new LogScanConfig(configMap);
        LogScanner scanner = new LogScanner(config);

        List<LogScanner.ScanMatch> matches = scanner.scanLogFile(logFile);

        // Should match all three lines
        assertEquals(3, matches.size());
    }

    /**
     * Tests scanning for memory limit pattern.
     */
    @Test
    void testMemoryLimitPattern() throws IOException
    {
        Path logFile = tempDir.resolve("test.log");
        Files.writeString(logFile,
            "Task started\n" +
            "Processing data\n" +
            "Exceeded job memory limit\n" +
            "Task failed\n"
        );

        LogScanConfig config = new LogScanConfig(null); // Uses default pattern
        LogScanner scanner = new LogScanner(config);

        List<LogScanner.ScanMatch> matches = scanner.scanLogFile(logFile);

        assertEquals(1, matches.size());
        assertEquals(3, matches.get(0).getLineNumber());
        assertEquals(137, matches.get(0).getPattern().getExitCode());
    }
}

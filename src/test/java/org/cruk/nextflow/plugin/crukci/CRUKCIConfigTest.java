package org.cruk.nextflow.plugin.crukci;

import static org.cruk.nextflow.plugin.crukci.CRUKCIConfig.DEFAULT_LINES_TO_SCAN;
import static org.cruk.nextflow.plugin.crukci.CRUKCIConfig.MINIMUM_JAVA_METASPACE;
import static org.cruk.nextflow.plugin.crukci.CRUKCIConfig.MINIMUM_JAVA_OVERHEAD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nextflow.Session;
import nextflow.util.MemoryUnit;

/**
 * Tests for CRUKCIConfig.
 */
@ExtendWith(MockitoExtension.class)
class CRUKCIConfigTest
{
    static final MemoryUnit DEFAULT_JAVA_OVERHEAD = MemoryUnit.of(CRUKCIConfig.DEFAULT_JAVA_OVERHEAD);
    static final MemoryUnit DEFAULT_JAVA_METASPACE = MemoryUnit.of(CRUKCIConfig.DEFAULT_JAVA_METASPACE);

    @Mock
    Session session;

    CRUKCIConfigTest() { }

    private void setup(Map<String, Object> config)
    {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("crukci", config);
        lenient().when(session.getConfig()).thenReturn(configMap);
    }

    private void checkDefaultPatterns(CRUKCIConfig config, int from)
    {
        assertEquals("Memory Limit Exceeded", config.patterns.get(from).name);
        assertEquals(137, config.patterns.get(from).exitCode);

        assertEquals("Java Heap Exhausted", config.patterns.get(from + 1).name);
        assertEquals(137, config.patterns.get(from + 1).exitCode);
    }

    /**
     * Tests default configuration values.
     */
    @Test
    void testDefaultConfig()
    {
        setup(null);

        CRUKCIConfig config = new CRUKCIConfig(session);

        assertEquals(DEFAULT_JAVA_OVERHEAD, config.javaOverhead);
        assertEquals(DEFAULT_JAVA_METASPACE, config.javaMetaspace);
        assertEquals(DEFAULT_LINES_TO_SCAN, config.maxLinesToScan);

        // Should have default memory limit pattern
        assertEquals(2, config.patterns.size());

        checkDefaultPatterns(config, 0);
    }

    /**
     * Tests custom configuration values.
     */
    @Test
    void testCustomConfig()
    {
        Map<String, Object> configMap = Map.of(
            "javaOverhead", "256M",
            "javaMetaspace", "512M",
            "maxLinesToScan", 5000
        );

        setup(configMap);

        CRUKCIConfig config = new CRUKCIConfig(session);

        assertEquals(5000, config.maxLinesToScan);
        assertEquals(256, config.javaOverhead.toMega());
        assertEquals(512, config.javaMetaspace.toMega());
    }

    /**
     * Test under minimum for JVM overheads.
     */
    @Test
    void testUnderMinimums()
    {
        Map<String, Object> configMap = Map.of(
            "javaOverhead", 8,
            "javaMetaspace", 8
        );

        setup(configMap);

        CRUKCIConfig config = new CRUKCIConfig(session);

        assertEquals(MINIMUM_JAVA_OVERHEAD, config.javaOverhead.toMega());
        assertEquals(MINIMUM_JAVA_METASPACE, config.javaMetaspace.toMega());
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

        setup(configMap);

        CRUKCIConfig config = new CRUKCIConfig(session);

        assertEquals(4, config.patterns.size());

        assertEquals("ERROR", config.patterns.get(0).name);
        assertEquals("WARNING", config.patterns.get(1).name);
        assertNull(config.patterns.get(0).exitCode);
        assertNull(config.patterns.get(1).exitCode);

        checkDefaultPatterns(config, 2);
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

        setup(configMap);

        CRUKCIConfig config = new CRUKCIConfig(session);

        assertEquals(5, config.patterns.size());

        assertEquals("Error Pattern", config.patterns.get(0).name);
        assertEquals("Warning Pattern", config.patterns.get(1).name);
        assertEquals("Memory", config.patterns.get(2).name);
        assertNull(config.patterns.get(0).exitCode);
        assertNull(config.patterns.get(1).exitCode);
        assertEquals(137, config.patterns.get(2).exitCode);

        checkDefaultPatterns(config, 3);
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

        setup(configMap);

        CRUKCIConfig config = new CRUKCIConfig(session);

        assertEquals(4, config.patterns.size());
        assertEquals(137, config.patterns.get(0).exitCode);
        assertNull(config.patterns.get(1).exitCode);

        checkDefaultPatterns(config, 2);
    }
}

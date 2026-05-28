package org.cruk.nextflow.plugin.crukci.extension;

import static org.cruk.nextflow.plugin.crukci.CRUKCIConfig.MINIMUM_JAVA_HEAP;
import static org.cruk.nextflow.plugin.crukci.CRUKCIConfig.MINIMUM_JAVA_METASPACE;
import static org.cruk.nextflow.plugin.crukci.CRUKCIConfig.MINIMUM_JAVA_OVERHEAD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cruk.nextflow.plugin.crukci.CRUKCIConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import groovy.util.Expando;
import nextflow.Session;
import nextflow.processor.TaskConfig;
import nextflow.script.ScriptBinding;
import nextflow.util.MemoryUnit;

/**
 * Unit tests for CRUKCIExtension.
 *
 * @author Richard Bowers
 */
@ExtendWith(MockitoExtension.class)
class CRUKCIExtensionTest
{
    static final MemoryUnit DEFAULT_JAVA_OVERHEAD = MemoryUnit.of(CRUKCIConfig.DEFAULT_JAVA_OVERHEAD);
    static final MemoryUnit DEFAULT_JAVA_METASPACE = MemoryUnit.of(CRUKCIConfig.DEFAULT_JAVA_METASPACE);

    @Mock
    private Session session;

    @Mock
    private ScriptBinding binding;

    private Map<String, Object> config;
    private Map<String, Object> crukciConfig;

    private CRUKCIExtension extension;

    CRUKCIExtensionTest() { }

    /**
     * Set up the test fixture before each test.
     */
    @BeforeEach
    void setUp()
    {
        extension = new CRUKCIExtension();

        // Initialize the extension with a mock session
        ScriptBinding.ParamsMap params = new ScriptBinding.ParamsMap();
        lenient().when(session.getParams()).thenReturn(params);
        lenient().when(session.getBinding()).thenReturn(binding);
        lenient().when(binding.getParams()).thenReturn(params);

        config = new HashMap<>();
        crukciConfig = new HashMap<>();
        config.put("crukci", crukciConfig);

        lenient().when(session.getConfig()).thenReturn(config);

        extension.init(session);
    }

    /**
     * Test javaMemMB with sufficient memory.
     */
    @Test
    @SuppressWarnings("deprecation")
    void testJavaMemMB_SufficientMemory()
    {
        TaskConfig task = new TaskConfig();
        task.put("memory", "512MB");

        Number result = extension.javaMemMB(task);

        assertEquals(320L, result.longValue()); // 512 - 128 metaspace - 64 overhead
    }

    /**
     * Test javaMemMB with exactly minimum memory.
     */
    @Test
    @SuppressWarnings("deprecation")
    void testJavaMemMB_MinimumMemory()
    {
        final long min = MINIMUM_JAVA_OVERHEAD.plus(MINIMUM_JAVA_METASPACE).plus(MINIMUM_JAVA_HEAP).toMega();

        crukciConfig.put("javaOverhead", MINIMUM_JAVA_OVERHEAD.toMega() + "M");
        crukciConfig.put("javaMetaspace", MINIMUM_JAVA_METASPACE.toMega() + "M");

        TaskConfig task = new TaskConfig();
        task.put("memory", min + "MB");

        Number result = extension.javaMemMB(task);

        assertEquals(16L, result.longValue());
    }

    /**
     * Test javaMemMB with insufficient memory throws exception.
     */
    @Test
    @SuppressWarnings("deprecation")
    void testJavaMemMB_InsufficientMemory()
    {
        final long min = MINIMUM_JAVA_OVERHEAD.plus(MINIMUM_JAVA_METASPACE).plus(MINIMUM_JAVA_HEAP).toMega();

        crukciConfig.put("javaOverhead", MINIMUM_JAVA_OVERHEAD.toMega() + "M");
        crukciConfig.put("javaMetaspace", MINIMUM_JAVA_METASPACE.toMega() + "M");

        TaskConfig task = new TaskConfig();
        task.put("memory", (min - 32) + "MB"); // Less than minimum

        InsufficientMemoryException exception = assertThrows(InsufficientMemoryException.class, () -> {
            extension.javaMemMB(task);
        });

        assertTrue(exception.getMessage().contains(min + " MB"));
    }

    /**
     * Test javaMemoryOptions with default parameters.
     */
    @Test
    void testJavaMemoryOptions_DefaultParameters()
    {
        TaskConfig task = new TaskConfig();
        task.put("memory", "1024MB");
        task.put("attempt", 1);

        Expando result = extension.javaMemoryOptions(task);

        assertNotNull(result);

        final MemoryUnit taskAllocation = MemoryUnit.of(1024L << 20);
        final MemoryUnit heap = taskAllocation.minus(DEFAULT_JAVA_METASPACE).minus(DEFAULT_JAVA_OVERHEAD);

        @SuppressWarnings("unchecked")
        Map<String, Object> props = result.getProperties();

        assertEquals(heap, props.get("heap")); // 1024 - 128 meta - 64 overhead
        assertEquals(DEFAULT_JAVA_METASPACE, props.get("metaSpace"));
        assertEquals(DEFAULT_JAVA_OVERHEAD, props.get("misc"));
        assertEquals(taskAllocation, props.get("all"));
        assertEquals("-XX:MaxMetaspaceSize=" + DEFAULT_JAVA_METASPACE.toMega() + "m -Xms" + heap.toMega() + "m -Xmx" + heap.toMega() + "m",
                     props.get("jvmOpts").toString());
    }

    /**
     * Test javaMemoryOptions with custom parameters.
     */
    @Test
    void testJavaMemoryOptions_CustomParameters()
    {
        crukciConfig.put("javaOverhead", "100M");
        crukciConfig.put("javaMetaspace", "256M");

        TaskConfig task = new TaskConfig();
        task.put("memory", "2048MB");
        task.put("attempt", 1);

        Expando result = extension.javaMemoryOptions(task);

        final MemoryUnit taskAllocation = MemoryUnit.of(2048L << 20);
        final MemoryUnit overhead = MemoryUnit.of(100L << 20);
        final MemoryUnit metaspace = MemoryUnit.of(256L << 20);
        final MemoryUnit heap = taskAllocation.minus(overhead).minus(metaspace);

        @SuppressWarnings("unchecked")
        Map<String, Object> props = result.getProperties();

        assertEquals(heap, props.get("heap"));
        assertEquals(metaspace, props.get("metaSpace"));
        assertEquals(overhead, props.get("misc"));
        assertEquals(MemoryUnit.of(2048L << 20), props.get("all"));
        assertEquals("-XX:MaxMetaspaceSize=" + metaspace.toMega() + "m -Xms" + heap.toMega() + "m -Xmx" + heap.toMega() + "m",
                     props.get("jvmOpts").toString());
    }

    /**
     * Test javaMemoryOptions with parameters below minimum.
     */
    @Test
    void testJavaMemoryOptions_ParametersBelowMinimum()
    {
        crukciConfig.put("javaOverhead", "8M");
        crukciConfig.put("javaMetaspace", "8M");

        TaskConfig task = new TaskConfig();
        task.put("memory", "1024MB");
        task.put("attempt", 1);

        Expando result = extension.javaMemoryOptions(task);

        MemoryUnit taskAllocation = MemoryUnit.of(1024L << 20);

        final MemoryUnit heap = taskAllocation.minus(MINIMUM_JAVA_METASPACE).minus(MINIMUM_JAVA_OVERHEAD);

        @SuppressWarnings("unchecked")
        Map<String, Object> props = result.getProperties();

        // Should be clamped to minimum values
        assertEquals(heap, props.get("heap"));
        assertEquals(MINIMUM_JAVA_METASPACE, props.get("metaSpace"));
        assertEquals(MINIMUM_JAVA_OVERHEAD, props.get("misc"));

        assertEquals("-XX:MaxMetaspaceSize=" + MINIMUM_JAVA_METASPACE.toMega() + "m -Xms" + heap.toMega() + "m -Xmx" + heap.toMega() + "m",
                     props.get("jvmOpts").toString());
    }

    /**
     * Test javaMemoryOptions with insufficient memory throws exception.
     */
    @Test
    void testJavaMemoryOptions_InsufficientMemory()
    {
        TaskConfig task = new TaskConfig();
        task.put("memory", "100MB"); // Too small
        task.put("attempt", 1);
        task.put("name", "testTask");

        assertThrows(InsufficientMemoryException.class, () -> { extension.javaMemoryOptions(task); });
    }

    /**
     * Test sizeOf with a Collection.
     */
    @Test
    void testSizeOf_Collection()
    {
        List<String> list = Arrays.asList("one", "two", "three");

        int result = extension.sizeOf(list);

        assertEquals(3, result);
    }

    /**
     * Test sizeOf with an empty Collection.
     */
    @Test
    void testSizeOf_EmptyCollection()
    {
        List<String> list = Collections.emptyList();

        int result = extension.sizeOf(list);

        assertEquals(0, result);
    }

    /**
     * Test sizeOf with a Map.
     */
    @Test
    void testSizeOf_Map()
    {
        Map<String, Integer> map = new HashMap<>();
        map.put("a", 1);
        map.put("b", 2);

        int result = extension.sizeOf(map);

        assertEquals(2, result);
    }

    /**
     * Test sizeOf with a single object.
     */
    @Test
    void testSizeOf_SingleObject()
    {
        String str = "single";

        int result = extension.sizeOf(str);

        assertEquals(1, result);
    }

    /**
     * Test sizeOf with null.
     */
    @Test
    void testSizeOf_Null()
    {
        int result = extension.sizeOf(null);

        assertEquals(0, result);
    }

    /**
     * Test makeCollection with a Collection.
     */
    @Test
    void testMakeCollection_AlreadyCollection()
    {
        List<String> list = Arrays.asList("one", "two");

        Collection<?> result = extension.makeCollection(list);

        assertSame(list, result);
    }

    /**
     * Test makeCollection with a single object.
     */
    @Test
    void testMakeCollection_SingleObject()
    {
        String str = "single";

        Collection<?> collection = extension.makeCollection(str);
        assertEquals(1, collection.size());
        assertTrue(collection.contains("single"));
    }

    /**
     * Test makeCollection with null.
     */
    @Test
    void testMakeCollection_Null()
    {
        Collection<?> result = extension.makeCollection(null);

        assertNull(result);
    }

    /**
     * Test safeName with alphanumeric characters.
     */
    @Test
    void testSafeName_Alphanumeric()
    {
        String result = extension.safeName("Sample123");

        assertEquals("Sample123", result);
    }

    /**
     * Test safeName with spaces.
     */
    @Test
    void testSafeName_WithSpaces()
    {
        String result = extension.safeName("Sample With Spaces");

        assertEquals("SampleWithSpaces", result);
    }

    /**
     * Test safeName with tabs.
     */
    @Test
    void testSafeName_WithTabs()
    {
        String result = extension.safeName("Sample\tWith\tTabs");

        assertEquals("SampleWithTabs", result);
    }

    /**
     * Test safeName with special characters.
     */
    @Test
    void testSafeName_SpecialCharacters()
    {
        String result = extension.safeName("Sample@#$%Name");

        assertEquals("Sample____Name", result);
    }

    /**
     * Test safeName with allowed special characters.
     */
    @Test
    void testSafeName_AllowedSpecialCharacters()
    {
        String result = extension.safeName("Sample_Name-v1.0");

        assertEquals("Sample_Name-v1.0", result);
    }

    /**
     * Test safeName with mixed characters.
     */
    @Test
    void testSafeName_MixedCharacters()
    {
        String result = extension.safeName("Sample (Name) [v2.0]");

        assertEquals("Sample_Name__v2.0_", result);
    }

    /**
     * Test safeName with unicode characters.
     */
    @Test
    void testSafeName_UnicodeCharacters()
    {
        String result = extension.safeName("Sample_Ñame_日本語");

        // Non-ASCII alphanumeric should be converted to underscores
        assertEquals("Sample__ame____", result);
    }

    /**
     * Test logException with a regular exception.
     */
    @Test
    void testLogException_RegularException()
    {
        RuntimeException exception = new RuntimeException("Test exception");

        Exception thrown = assertThrows(RuntimeException.class, () -> {
            extension.logException(exception);
        });

        assertSame(exception, thrown);
    }

    /**
     * Test logException with InvocationTargetException.
     */
    @Test
    void testLogException_InvocationTargetException()
    {
        RuntimeException cause = new RuntimeException("Inner exception");
        InvocationTargetException exception = new InvocationTargetException(cause);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> {
            extension.logException(exception);
        });

        assertSame(exception, thrown);
    }

    /**
     * Test logException with nested InvocationTargetException.
     */
    @Test
    void testLogException_NestedInvocationTargetException()
    {
        IllegalArgumentException innerCause = new IllegalArgumentException("Deepest exception");
        InvocationTargetException exception = new InvocationTargetException(innerCause);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> {
            extension.logException(exception);
        });

        assertSame(exception, thrown);
        assertEquals(innerCause, thrown.getCause());
    }
}

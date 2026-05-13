package org.cruk.nextflow.plugin.crukci.extension;

import static org.cruk.nextflow.plugin.crukci.CRUKCIConfig.MINIMUM_JAVA_METASPACE;
import static org.cruk.nextflow.plugin.crukci.CRUKCIConfig.MINIMUM_JAVA_OVERHEAD;
import static org.cruk.nextflow.plugin.crukci.CRUKCIConfig.MINIMUM_JAVA_HEAP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
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
    void testJavaMemMB_SufficientMemory()
    {
        TaskConfig task = new TaskConfig();
        task.put("memory", "512MB");

        Number result = (Number)extension.javaMemMB(task);

        assertEquals(320L, result.longValue()); // 512 - 128 metaspace - 64 overhead
    }

    /**
     * Test javaMemMB with exactly minimum memory.
     */
    @Test
    void testJavaMemMB_MinimumMemory()
    {
        final long min = MINIMUM_JAVA_OVERHEAD + MINIMUM_JAVA_METASPACE + MINIMUM_JAVA_HEAP;

        crukciConfig.put("javaOverhead", MINIMUM_JAVA_OVERHEAD + "M");
        crukciConfig.put("javaMetaspace", MINIMUM_JAVA_METASPACE + "M");

        TaskConfig task = new TaskConfig();
        task.put("memory", min + "MB");

        Number result = (Number)extension.javaMemMB(task);

        assertEquals(16L, result.longValue());
    }

    /**
     * Test javaMemMB with insufficient memory throws exception.
     */
    @Test
    void testJavaMemMB_InsufficientMemory()
    {
        final long min = MINIMUM_JAVA_OVERHEAD + MINIMUM_JAVA_METASPACE + MINIMUM_JAVA_HEAP;

        crukciConfig.put("javaOverhead", MINIMUM_JAVA_OVERHEAD + "M");
        crukciConfig.put("javaMetaspace", MINIMUM_JAVA_METASPACE + "M");

        TaskConfig task = new TaskConfig();
        task.put("memory", (min - 32) + "MB"); // Less than minimum

        Exception exception = assertThrows(Exception.class, () -> {
            extension.javaMemMB(task);
        });

        assertTrue(exception.getMessage().contains("No memory left after taking JVM overheads."));
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

        Object result = extension.javaMemoryOptions(task);

        assertNotNull(result);

        final long heap = 1024L - DEFAULT_JAVA_METASPACE.toMega() - DEFAULT_JAVA_OVERHEAD.toMega();

        // Access Expando properties using reflection
        Map<String, Object> props = getExpandoProperties(result);

        assertEquals(heap, ((Number) props.get("heap")).longValue()); // 1024 - 128 meta - 64 overhead
        assertEquals(DEFAULT_JAVA_METASPACE.toMega(), (Long)props.get("metaSpace"));
        assertEquals(DEFAULT_JAVA_OVERHEAD.toMega(), (Long)props.get("misc"));
        assertEquals(1024L, (Long)props.get("all"));
        assertEquals("-XX:MaxMetaspaceSize=" + DEFAULT_JAVA_METASPACE.toMega() + "m -Xms" + heap + "m -Xmx" + heap +"m", props.get("jvmOpts").toString());
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

        Object result = extension.javaMemoryOptions(task);

        final long heap = 2048L - 100 - 256;

        Map<String, Object> props = getExpandoProperties(result);

        assertEquals(heap, ((Number) props.get("heap")).longValue()); // 2048 - 256 meta - 100 overhead
        assertEquals(256L, ((Number) props.get("metaSpace")).intValue());
        assertEquals(100L, ((Number) props.get("misc")).intValue());
        assertEquals(2048L, ((Number) props.get("all")).intValue());
        assertEquals("-XX:MaxMetaspaceSize=256m -Xms" + heap + "m -Xmx" + heap + "m", props.get("jvmOpts").toString());
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

        Object result = extension.javaMemoryOptions(task);

        final long heap = 1024L - MINIMUM_JAVA_METASPACE - MINIMUM_JAVA_OVERHEAD;

        Map<String, Object> props = getExpandoProperties(result);

        // Should be clamped to minimum values
        assertEquals(heap, ((Number) props.get("heap")).longValue()); // 1024 - 64 meta - 32 overhead
        assertEquals(MINIMUM_JAVA_METASPACE, ((Number) props.get("metaSpace")).intValue()); // Clamped to minimum
        assertEquals(MINIMUM_JAVA_OVERHEAD, ((Number) props.get("misc")).intValue()); // Clamped to minimum

        assertEquals("-XX:MaxMetaspaceSize=" + MINIMUM_JAVA_METASPACE + "m -Xms" + heap + "m -Xmx" + heap + "m", props.get("jvmOpts").toString());
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

        Exception exception = assertThrows(Exception.class, () -> {
            extension.javaMemoryOptions(task);
        });

        assertTrue(exception.getMessage().contains("No memory left after taking JVM overheads"));
    }

    /**
     * Test sizeOf with a Collection.
     */
    @Test
    void testSizeOf_Collection()
    {
        List<String> list = Arrays.asList("one", "two", "three");

        Object result = extension.sizeOf(list);

        assertEquals(3, ((Number) result).intValue());
    }

    /**
     * Test sizeOf with an empty Collection.
     */
    @Test
    void testSizeOf_EmptyCollection()
    {
        List<String> list = Collections.emptyList();

        Object result = extension.sizeOf(list);

        assertEquals(0, ((Number) result).intValue());
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

        Object result = extension.sizeOf(map);

        assertEquals(2, ((Number) result).intValue());
    }

    /**
     * Test sizeOf with a single object.
     */
    @Test
    void testSizeOf_SingleObject()
    {
        String str = "single";

        Object result = extension.sizeOf(str);

        assertEquals(1, ((Number) result).intValue());
    }

    /**
     * Test sizeOf with null.
     */
    @Test
    void testSizeOf_Null()
    {
        Object result = extension.sizeOf(null);

        assertEquals(0, ((Number) result).intValue());
    }

    /**
     * Test makeCollection with a Collection.
     */
    @Test
    void testMakeCollection_AlreadyCollection()
    {
        List<String> list = Arrays.asList("one", "two");

        Object result = extension.makeCollection(list);

        assertSame(list, result);
    }

    /**
     * Test makeCollection with a single object.
     */
    @Test
    void testMakeCollection_SingleObject()
    {
        String str = "single";

        Object result = extension.makeCollection(str);

        assertTrue(result instanceof Collection);
        Collection<?> collection = (Collection<?>) result;
        assertEquals(1, collection.size());
        assertTrue(collection.contains("single"));
    }

    /**
     * Test makeCollection with null.
     */
    @Test
    void testMakeCollection_Null()
    {
        Object result = extension.makeCollection(null);

        assertNull(result);
    }

    /**
     * Test safeName with alphanumeric characters.
     */
    @Test
    void testSafeName_Alphanumeric()
    {
        Object result = extension.safeName("Sample123");

        assertEquals("Sample123", result.toString());
    }

    /**
     * Test safeName with spaces.
     */
    @Test
    void testSafeName_WithSpaces()
    {
        Object result = extension.safeName("Sample With Spaces");

        assertEquals("SampleWithSpaces", result.toString());
    }

    /**
     * Test safeName with tabs.
     */
    @Test
    void testSafeName_WithTabs()
    {
        Object result = extension.safeName("Sample\tWith\tTabs");

        assertEquals("SampleWithTabs", result.toString());
    }

    /**
     * Test safeName with special characters.
     */
    @Test
    void testSafeName_SpecialCharacters()
    {
        Object result = extension.safeName("Sample@#$%Name");

        assertEquals("Sample____Name", result.toString());
    }

    /**
     * Test safeName with allowed special characters.
     */
    @Test
    void testSafeName_AllowedSpecialCharacters()
    {
        Object result = extension.safeName("Sample_Name-v1.0");

        assertEquals("Sample_Name-v1.0", result.toString());
    }

    /**
     * Test safeName with mixed characters.
     */
    @Test
    void testSafeName_MixedCharacters()
    {
        Object result = extension.safeName("Sample (Name) [v2.0]");

        assertEquals("Sample_Name__v2.0_", result.toString());
    }

    /**
     * Test safeName with unicode characters.
     */
    @Test
    void testSafeName_UnicodeCharacters()
    {
        Object result = extension.safeName("Sample_Ñame_日本語");

        // Non-ASCII alphanumeric should be converted to underscores
        assertEquals("Sample__ame____", result.toString());
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

    /**
     * Helper method to extract properties from Groovy Expando object.
     *
     * @param expando The Expando object.
     * @return Map of property names to values.
     */
    private Map<String, Object> getExpandoProperties(Object expando)
    {
        Map<String, Object> props = new HashMap<>();
        try
        {
            // Expando properties can be accessed via getProperties() method
            java.lang.reflect.Method getPropertiesMethod = expando.getClass().getMethod("getProperties");
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) getPropertiesMethod.invoke(expando);
            props.putAll(properties);
        }
        catch (Exception e)
        {
            fail("Failed to extract Expando properties: " + e.getMessage());
        }
        return props;
    }
}

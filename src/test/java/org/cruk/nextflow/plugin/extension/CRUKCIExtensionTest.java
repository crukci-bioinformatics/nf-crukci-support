package org.cruk.nextflow.plugin.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nextflow.Session;
import nextflow.processor.TaskConfig;
import nextflow.script.ScriptBinding;

/**
 * Unit tests for CRUKCIExtension.
 *
 * @author Richard Bowers
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class CRUKCIExtensionTest
{
    @Mock
    private Session session;

    @Mock
    private ScriptBinding binding;

    private CRUKCIExtension extension;

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

        Object result = extension.javaMemMB(task);

        assertEquals(384L, ((Number) result).longValue()); // 512 - 128 overhead
    }

    /**
     * Test javaMemMB with exactly minimum memory.
     */
    @Test
    void testJavaMemMB_MinimumMemory()
    {
        TaskConfig task = new TaskConfig();
        task.put("memory", "144MB"); // 128 overhead + 16 minimum heap

        Object result = extension.javaMemMB(task);

        assertEquals(16L, ((Number) result).longValue());
    }

    /**
     * Test javaMemMB with insufficient memory throws exception.
     */
    @Test
    void testJavaMemMB_InsufficientMemory()
    {
        TaskConfig task = new TaskConfig();
        task.put("memory", "100MB"); // Less than 144MB minimum

        Exception exception = assertThrows(Exception.class, () -> {
            extension.javaMemMB(task);
        });

        assertTrue(exception.getMessage().contains("No memory after taking JVM overhead"));
        assertTrue(exception.getMessage().contains("144 MB"));
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

        // Access Expando properties using reflection
        Map<String, Object> props = getExpandoProperties(result);

        assertEquals(832L, ((Number) props.get("heap")).longValue()); // 1024 - 128 meta - 64 overhead
        assertEquals(128L, ((Number) props.get("metaSpace")).longValue());
        assertEquals(64L, ((Number) props.get("misc")).longValue());
        assertEquals(1024L, ((Number) props.get("all")).longValue());
        assertEquals("-XX:MaxMetaspaceSize=128m -Xms832m -Xmx832m", props.get("jvmOpts").toString());
    }

    /**
     * Test javaMemoryOptions with custom parameters.
     */
    @Test
    void testJavaMemoryOptions_CustomParameters()
    {
        // Set custom parameters
        ScriptBinding.ParamsMap params = new ScriptBinding.ParamsMap();
        params.put("java_overhead_size", 100);
        params.put("java_metaspace_size", 256);
        when(session.getParams()).thenReturn(params);

        TaskConfig task = new TaskConfig();
        task.put("memory", "2048MB");
        task.put("attempt", 1);

        Object result = extension.javaMemoryOptions(task);

        Map<String, Object> props = getExpandoProperties(result);

        assertEquals(1692L, ((Number) props.get("heap")).longValue()); // 2048 - 256 meta - 100 overhead
        assertEquals(256L, ((Number) props.get("metaSpace")).longValue());
        assertEquals(100L, ((Number) props.get("misc")).longValue());
        assertEquals(2048L, ((Number) props.get("all")).longValue());
        assertEquals("-XX:MaxMetaspaceSize=256m -Xms1692m -Xmx1692m", props.get("jvmOpts").toString());
    }

    /**
     * Test javaMemoryOptions with retry attempts scaling.
     */
    @Test
    void testJavaMemoryOptions_WithRetryAttempt()
    {
        ScriptBinding.ParamsMap params = new ScriptBinding.ParamsMap();
        params.put("java_overhead_size", 64);
        params.put("java_metaspace_size", 128);
        when(session.getParams()).thenReturn(params);

        TaskConfig task = new TaskConfig();
        task.put("memory", "4096MB");
        task.put("attempt", 2); // Second attempt

        Object result = extension.javaMemoryOptions(task);

        Map<String, Object> props = getExpandoProperties(result);

        // overhead and metaspace should be multiplied by attempt
        assertEquals(3712L, ((Number) props.get("heap")).longValue()); // 4096 - (256 meta * 2) - (64 overhead * 2)
        assertEquals(256L, ((Number) props.get("metaSpace")).longValue()); // 128 * 2
        assertEquals(128L, ((Number) props.get("misc")).longValue()); // 64 * 2
    }

    /**
     * Test javaMemoryOptions with parameters below minimum.
     */
    @Test
    void testJavaMemoryOptions_ParametersBelowMinimum()
    {
        ScriptBinding.ParamsMap params = new ScriptBinding.ParamsMap();
        params.put("java_overhead_size", 10); // Below minimum of 32
        params.put("java_metaspace_size", 32); // Below minimum of 64
        when(session.getParams()).thenReturn(params);

        TaskConfig task = new TaskConfig();
        task.put("memory", "1024MB");
        task.put("attempt", 1);

        Object result = extension.javaMemoryOptions(task);

        Map<String, Object> props = getExpandoProperties(result);

        // Should be clamped to minimum values
        assertEquals(928L, ((Number) props.get("heap")).longValue()); // 1024 - 64 meta - 32 overhead
        assertEquals(64L, ((Number) props.get("metaSpace")).longValue()); // Clamped to minimum
        assertEquals(32L, ((Number) props.get("misc")).longValue()); // Clamped to minimum
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

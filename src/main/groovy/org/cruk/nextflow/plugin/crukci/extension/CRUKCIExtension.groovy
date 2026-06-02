package org.cruk.nextflow.plugin.crukci.extension

import java.lang.reflect.InvocationTargetException
import java.text.*

import org.cruk.nextflow.plugin.crukci.CRUKCIConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import nextflow.Session
import nextflow.plugin.extension.Function
import nextflow.plugin.extension.PluginExtensionPoint
import nextflow.processor.TaskConfig
import nextflow.util.MemoryUnit

/**
 * CRUK CI utility extension for Nextflow pipelines.
 * Provides helper functions for memory management, collection handling, and debugging.
 *
 * @author Richard Bowers
 */
class CRUKCIExtension extends PluginExtensionPoint
{
    private static final Logger logger = LoggerFactory.getLogger(CRUKCIExtension.class)

    private Session session

    /**
     * Initialize the extension with the Nextflow session.
     *
     * @param session The Nextflow session object.
     */
    @Override
    protected void init(Session session)
    {
        this.session = session
        logger.debug("CRUK CI Extension initialized")
    }

    /**
     * Give a number for the Java heap size based on the task memory, allowing for
     * some overhead for the JVM itself (metaspace and other) from the total allowed.
     *
     * @param task The task object containing memory allocation information.
     *
     * @return The calculated Java heap size in megabytes.
     *
     * @throws Exception If insufficient memory is available after overhead.
     *
     * @deprecated Use {@code javaMemoryOptions} in preference to this method.
     */
    @Function @Deprecated
    long javaMemMB(TaskConfig task)
    {
        return javaMemoryOptions(task).heap.mega
    }

    /**
     * Provide OpenJDK JVM memory configuration based on the memory given to the task.
     * Allocates a maximum Java meta space size, which is 128MB by default but can be
     * changed by configuring the plugin to set "javaMetaspaceSize", down to a minimum of
     * 64MB (no maximum). Likewise the "javaOverhead" parameter can give a size
     * for other memory overheads, down to a minimum of 32MB.
     * What's left of the task's memory after allocating the meta
     * space size plus the miscellaneous overhead is allocated for the JVM's heap.
     *
     * <p>
     * Returns an object with numerous fields (types in brackets):
     * <ol>
     * <li>"heap" - The heap size ({@code MemoryUnit}).</li>
     * <li>"metaSpace" - The meta space size ({@code MemoryUnit}).</li>
     * <li>"misc" - The additional overhead taken for everything else ({@code MemoryUnit}).</li>
     * <li>"all" - The task's allocated memory ({@code MemoryUnit}); same as {@code task.memory}.</li>
     * <li>"jvmOpts" - The string to include in the Java command line for the program
     * to set the memory values as calculated. This string must not be quoted in
     * the shell script.</li>
     * </ol>
     * </p>
     *
     * @param task The task object containing memory allocation information.
     *
     * @return An Expando object with {@code heap}, {@code metaSpace}, {@code misc},
     * {@code all} and {@code jvmOpts} fields.
     *
     * @throws InsufficientMemoryException if insufficient memory is available after overheads are
     * taken off the task allocation.
     *
     * @see MemoryUnit
     */
    @Function
    Expando javaMemoryOptions(TaskConfig task)
    {
        final MemoryUnit taskAllocation = task.memory

        final def crukciConfig = new CRUKCIConfig(session)

        MemoryUnit heap
        try
        {
            heap = taskAllocation.minus(crukciConfig.javaOverhead).minus(crukciConfig.javaMetaspace)
        }
        catch (AssertionError e)
        {
            // Thrown if the calculation above ends up being a negative number.
            // Deal with the problem by giving it zero memory, which is then
            // handled below in the same manner as a positive but too small
            // amount.
            heap = MemoryUnit.of(0L)
        }

        if ((heap <=> CRUKCIConfig.MINIMUM_JAVA_HEAP) < 0)
        {
            logger.error("Task {} attempt {}: allocated {}; JVM overhead {}; Java Meta Space {}.",
                         task.name, task.attempt, taskAllocation, crukciConfig.javaOverhead, crukciConfig.javaMetaspace)

            def requiredMin = crukciConfig.javaOverhead.plus(crukciConfig.javaMetaspace).plus(CRUKCIConfig.MINIMUM_JAVA_HEAP)
            throw new InsufficientMemoryException(taskAllocation, crukciConfig.javaOverhead, crukciConfig.javaMetaspace)
        }

        def info = new Expando()
        info.heap = heap
        info.metaSpace = crukciConfig.javaMetaspace
        info.misc = crukciConfig.javaOverhead
        info.all = taskAllocation
        info.jvmOpts = "-XX:MaxMetaspaceSize=${crukciConfig.javaMetaspace.mega}m -Xms${heap.mega}m -Xmx${heap.mega}m"

        return info
    }

    /**
     * Get the size of a collection of things. It might be that the thing
     * passed in isn't a collection or map, in which case the size is 1.
     * If null is passed in, return 0.
     *
     * See https://github.com/nextflow-io/nextflow/issues/2425
     *
     * See makeCollection below for Nextflow's own alternatives.
     *
     * @param thing The object whose size is to be determined.
     * @return The size of the collection, 1 for non-collections, or 0 for null.
     */
    @Function
    int sizeOf(Object thing)
    {
        if (thing instanceof Collection || thing instanceof Map)
        {
            return thing.size()
        }

        if (thing == null)
        {
            return 0
        }

        return 1
    }

    /**
     * Make sure a thing is a collection when required.
     * It might be that the thing passed in isn't a collection, in which
     * case make it a list containing the single thing.
     * If the thing is null, return null.
     *
     * See https://github.com/nextflow-io/nextflow/issues/2425
     *
     * This is resolved in Nextflow >= 23.9 with the "arity" attibute on
     * file and path. If arity is set to '1..*' a glob will return a
     * collection even if only one file is found to match the pattern.
     * Conversely, if arity is set to '1' a single file or path is returned
     * (i.e. not in a collection). Presumably an error is thrown if more
     * than one file matches.
     *
     * The "files" function can be used instead of "file" to create files
     * that will always be in a list, even if there is only one match.
     *
     * @param thingOrList The object to ensure is a collection.
     * @return A collection containing the object, or null if the input is null.
     */
    @Function
    Collection makeCollection(Object thingOrList)
    {
        if (thingOrList instanceof Collection)
        {
            return thingOrList
        }

        if (thingOrList != null)
        {
            return Collections.singletonList(thingOrList)
        }

        return null
    }

    /**
     * Make a name safe to be used as a file name. Everything that's not
     * alphanumeric, dot, underscore or hyphen is converted to an underscore.
     * Spaces are just removed.
     *
     * See https://github.com/nextflow-io/nextflow/issues/5234
     * and https://github.com/nextflow-io/nextflow/issues/5441
     *
     * @param name The name to make safe for use as a filename. Some things
     * can come into here that aren't string based (grouping key for example)
     * so this is just an object.
     *
     * @return A sanitized filename string.
     */
    @Function
    String safeName(Object name)
    {
        def nameStr = name == null ? '' : name.toString()
        def safe = new StringBuilder(nameStr.length())
        def iter = new StringCharacterIterator(nameStr)

        for (def c = iter.first(); c != CharacterIterator.DONE; c = iter.next())
        {
            switch (c)
            {
                case { org.apache.commons.lang3.CharUtils.isAsciiAlphanumeric(it) }:
                case '_':
                case '-':
                case '.':
                    safe << c
                    break

                case ' ':
                case '\t':
                    // Add nothing.
                    break

                default:
                    safe << '_'
                    break
            }
        }

        return safe.toString()
    }

    /**
     * Log an exception to the logger as an error, including the stack trace.
     * Looks for InvocationTargetExceptions, which occur quite often, and logs
     * the cause of that exception, not the wrapper exception.
     *
     * @param e The exception to log.
     * @throws Exception The original exception after logging.
     */
    @Function
    void logException(Throwable e)
    {
        def forLogging = e
        try
        {
            throw e
        }
        catch (InvocationTargetException ite)
        {
            forLogging = ite.targetException
        }
        catch (Throwable t)
        {
        }

        def sw = new StringWriter(1000)
        def pw = new PrintWriter(sw)
        forLogging.printStackTrace(pw)
        logger.error sw.toString()
        throw e
    }
}

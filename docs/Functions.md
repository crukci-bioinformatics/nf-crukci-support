# Extension Functions

The plugin provides utility functions that can be used in your Nextflow pipelines. These functions provide helper functionality for memory management, collection handling, and debugging.

### Including Extension Functions

To use the extension functions in your pipeline, add an `include` statement at the top of your `.nf` file:

```groovy
include { javaMemMB; javaMemoryOptions; sizeOf; makeCollection; safeName; logException } from 'plugin/nf-crukci-support'
```

You can include only the functions you need:

```groovy
include { javaMemMB; safeName } from 'plugin/nf-crukci-support'
```

### Available Functions

#### javaMemMB(task)

Calculates the Java heap size based on the task memory allocation, reserving 128 MB for JVM overhead.

**Parameters:**
- `task` - The task object containing memory allocation information

**Returns:** The calculated Java heap size in megabytes

**Throws:** Exception if insufficient memory is available after overhead (minimum 16 MB heap required)

**Example:**
```groovy
process myJavaProcess {
    memory '2 GB'

    script:
    def heapMB = javaMemMB(task)
    """
    java -Xmx${heapMB}m -jar myapp.jar
    """
}
```

#### javaMemoryOptions(task)

Provides comprehensive OpenJDK JVM memory configuration based on the task memory allocation. This function allocates memory for the Java heap, metaspace, and miscellaneous overhead.

**Parameters:**
- `task` - The task object containing memory allocation information

**Configuration parameters** (via `params`):
- `java_metaspace_size` - Maximum metaspace size in MB (default: 128, minimum: 64)
- `java_overhead_size` - Additional overhead for JNI, ByteBuffers, etc. in MB (default: 64, minimum: 32)

Both parameters are multiplied by `task.attempt` to scale with retry attempts.

**Returns:** An object with the following fields:
- `heap` - The heap size in MB
- `metaSpace` - The metaspace size in MB
- `misc` - The additional overhead in MB
- `all` - The total task memory allocation in MB
- `jvmOpts` - A string containing JVM options to set the calculated memory values

**Example:**
```groovy
process myJavaProcess {
    memory '4 GB'

    script:
    def mem = javaMemoryOptions(task)
    """
    echo "Heap: ${mem.heap} MB, MetaSpace: ${mem.metaSpace} MB, Total: ${mem.all} MB"
    java ${mem.jvmOpts} -jar myapp.jar
    """
}
```

**Configuration example:**
```groovy
params {
    java_metaspace_size = 256
    java_overhead_size = 128
}
```

#### sizeOf(thing)

Gets the size of a collection, map, or single object. Handles the common issue where Nextflow sometimes returns a single item instead of a collection.

**Parameters:**
- `thing` - The object whose size is to be determined

**Returns:**
- The size of the collection or map
- `1` for non-collection objects
- `0` for `null`

**Example:**
```groovy
def list = [1, 2, 3]
def single = "test"
def nullVal = null

println "Size of list: ${sizeOf(list)}"        // 3
println "Size of single: ${sizeOf(single)}"    // 1
println "Size of null: ${sizeOf(nullVal)}"     // 0
```

**See also:** Up do date Nextflow versions provided the `arity` attribute on `file` and `path`, and the `files()` function as alternatives.
This function is still here supporting older pipelines.

#### makeCollection(thingOrList)

Ensures that an object is wrapped in a collection. Useful for handling cases where Nextflow might return either a single item or a collection.
This is particularly necessary with files (actually `Path`s). The `size()` function one would expect to return the size of a collection,
but if a single file is returned this returns the length of the file. Wrapping in this function means `size()` is the size of the collection.

**Parameters:**
- `thingOrList` - The object to ensure is a collection

**Returns:**
- The original collection if input is already a collection
- A single-item list containing the object if input is not a collection
- `null` if input is `null`

**Example:**
```groovy
def single = "file.txt"
def collection = makeCollection(single)
println collection  // ["file.txt"]

def list = ["a.txt", "b.txt"]
def collection2 = makeCollection(list)
println collection2  // ["a.txt", "b.txt"]
```

**See also:** Nextflow >= 23.9 provides the `arity` attribute and `files()` function as alternatives.

#### safeName(name)

Sanitizes a string to make it safe for use as a filename. Converts unsafe characters to underscores and removes spaces.

**Parameters:**
- `name` - The name to make safe for use as a filename

**Returns:** A sanitized filename string

**Rules:**
- Alphanumeric characters, dots (`.`), underscores (`_`), and hyphens (`-`) are preserved
- Spaces and tabs are removed
- All other characters are converted to underscores

**Requirements:** This function requires Apache Commons Lang3. Add the JAR to a `lib/` directory in your pipeline:
```bash
mkdir -p lib
wget -P lib https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar
```

**Example:**
```groovy
def unsafe = "My File: Test (v1.0)"
def safe = safeName(unsafe)
println safe  // "MyFile_Testv1.0"

process createOutput {
    script:
    def sampleName = "Sample #123 (batch-1)"
    def outputFile = "${safeName(sampleName)}.txt"
    """
    echo "Processing ${sampleName}" > ${outputFile}
    """
}
```

#### logException(exception)

Logs an exception to the logger with full stack trace and then re-throws it. Automatically unwraps `InvocationTargetException` to log the underlying cause.

**Parameters:**
- `exception` - The exception to log

**Throws:** The original exception after logging

**Example:**
```groovy
try {
    // Some operation that might fail
    riskyOperation()
} catch (Exception e) {
    logException(e)
}
```

This is particularly useful for debugging complex workflows where exceptions might be wrapped or difficult to trace.

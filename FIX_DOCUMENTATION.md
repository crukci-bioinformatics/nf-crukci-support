# LogScan Plugin Fix - Exit Code Timing Issue

## Problem Summary

The nf-logscan plugin was experiencing two critical issues:

1. **Timing Problem**: The plugin ran too late in the Nextflow task lifecycle
2. **Exit Code Ignored**: Modifications to exit codes had no effect on error strategy and retry logic

### Root Cause

The plugin implemented `onTaskComplete()` which fires **AFTER** Nextflow has already:
- Evaluated the task exit code
- Applied the error strategy (RETRY, IGNORE, TERMINATE, FINISH)
- Made the decision whether to retry the task

Modifying `trace.put("exit", X)` at this point only updated logging/reporting records but did NOT affect:
- Task retry decisions
- Error strategy behavior
- Workflow continuation logic

## Solution Implemented

The fix uses a **bash wrapper approach** that modifies the `.exitcode` file BEFORE Nextflow reads it.

### How It Works

1. **Injection Point**: The plugin now hooks into `onProcessCreate()` to inject an `afterScript` into every process
2. **Timing**: The `afterScript` runs AFTER the main task script completes but BEFORE the Nextflow wrapper finalizes the task
3. **Exit Code Modification**: The script scans `.command.log` for patterns and overwrites `.exitcode` if a pattern matches
4. **Nextflow Integration**: Nextflow's wrapper (`.command.run`) reads `.exitcode` to determine the final task exit status

### Execution Flow

```
Task Script (.command.sh)
  ↓
Task completes with exit code → written to .exitcode
  ↓
afterScript runs (OUR PLUGIN CODE)
  ├─ Reads .exitcode
  ├─ Scans .command.log for patterns
  ├─ If pattern found: overwrites .exitcode with new exit code
  └─ Exits successfully (exit 0)
  ↓
Nextflow wrapper reads .exitcode (now modified)
  ↓
Task finalization with CORRECT exit code
  ↓
Error strategy applied with CORRECT exit code
  ↓
Retry/Ignore/Terminate based on MODIFIED exit code
```

## Code Changes

### Main Change: LogScanObserver.java

#### 1. Modified `onProcessCreate()` Method
```java
@Override
public void onProcessCreate(TaskProcessor processor)
{
    if (!config.isEnabled())
    {
        return;
    }

    try
    {
        injectLogScanAfterScript(processor);

        if (config.isVerbose())
        {
            logger.info("Injected log scan script into process: {}", processor.getName());
        }
    }
    catch (Exception e)
    {
        logger.error("Failed to inject log scan script for process: {}", processor.getName(), e);
    }
}
```

#### 2. Added `injectLogScanAfterScript()` Method
Injects the scanning script into the process configuration's `afterScript` directive.

#### 3. Added `buildLogScanScript()` Method
Generates a Bash script that:
- Reads original exit code from `.exitcode`
- Scans `.command.log` line by line
- Checks each line against configured patterns
- Overwrites `.exitcode` if a pattern with an exit code matches
- Reports matches to stderr (`>&2`)

#### 4. Added `escapeForBash()` Helper Method
Properly escapes strings for use in Bash single-quoted strings.

#### 5. Updated `onTaskComplete()` Method
Simplified to only perform verbose logging, since the real work happens in the injected `afterScript`.

## Technical Details

### Why This Works

**Nextflow's Task Wrapper Structure:**
```bash
# .command.run (simplified)
bash .command.sh
echo $? > .exitcode

# Run afterScript if configured
if [ -f .afterScript.sh ]; then
  bash .afterScript.sh
fi

# Read exit code and return
EXIT_CODE=$(cat .exitcode)
exit $EXIT_CODE
```

By modifying `.exitcode` in the `afterScript`, we change the value that Nextflow reads and uses for error strategy evaluation.

### Pattern Matching in Bash

The injected script uses `grep` for pattern matching:
```bash
if echo "$line" | grep -qE 'pattern'; then
  echo "137" > .exitcode  # Override exit code
fi
```

Flags:
- `-E`: Extended regex (same as Java Pattern)
- `-i`: Case-insensitive (when pattern was compiled with CASE_INSENSITIVE flag)
- `-q`: Quiet mode (no output, just exit status)

### Configuration Support

The script respects all configuration options:
- `scanOnSuccess`: Only scan if original exit code == 0
- `scanOnFailure`: Only scan if original exit code != 0
- `maxLinesToScan`: Stop scanning after N lines (0 = unlimited)
- Pattern-specific exit codes

## Testing

To test the fix:

1. **Build and install:**
   ```bash
   cd /home/bowers01/work/nextflow/nf-crukci-support4/nf-crukci-support
   mvn clean package
   mkdir -p ~/.nextflow/plugins/nf-logscan-1.0.0-SNAPSHOT
   cp target/nf-crukci-logscan-1.0.0-SNAPSHOT.jar ~/.nextflow/plugins/nf-logscan-1.0.0-SNAPSHOT/
   ```

2. **Run test pipeline:**
   ```bash
   cd /mnt/scratchc/bioinformatics/bowers01/overtime
   nextflow run <pipeline> -c <config>
   ```

3. **Expected behavior:**
   - Tasks that exceed memory limits will have exit code set to 137
   - If `errorStrategy = 'retry'` is configured, tasks will be retried
   - Pattern matches will be reported to stderr:
     ```
     [LogScan] Pattern 'Memory Limit Exceeded' matched at line 3
     [LogScan] Original exit: -, New exit: 137
     [LogScan] Updated .exitcode with new exit code
     ```

## Benefits

✅ **Immediate Response**: Script runs as soon as task completes
✅ **Correct Exit Code**: Nextflow sees the modified exit code BEFORE making retry decisions
✅ **Error Strategy Respected**: RETRY, IGNORE, TERMINATE all work as expected
✅ **No Performance Impact**: Simple bash script with minimal overhead
✅ **Transparent**: All modifications logged to stderr for debugging

## Backwards Compatibility

The change is fully backwards compatible:
- Existing `onTaskComplete()` logging still works (in verbose mode)
- Configuration format unchanged
- Pattern definitions unchanged
- If plugin is disabled, no scripts are injected

## Future Enhancements

Potential improvements:
1. Add configuration to control which processes get the afterScript (include/exclude patterns)
2. Support for custom exit code mapping tables
3. Option to scan other files besides .command.log
4. Support for multi-line pattern matching in bash script

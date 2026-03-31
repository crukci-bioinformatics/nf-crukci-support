# Nextflow Plugin Trigger Delay Analysis

## Problem Summary

The nf-crukci-support plugin experiences a ~7.5 minute delay before being triggered when SLURM kills a task due to memory limits. This is **not a plugin issue** but rather a fundamental limitation of how Nextflow detects externally-killed SLURM jobs.

## Timeline from Test Run

```
15:09:52  Task starts
15:10:02  SLURM kills task (OOM) - visible in .command.log
15:10:02  
to        Nextflow polling and waiting period
15:17:31  
15:17:31  Nextflow detects completion
15:17:31  Plugin immediately scans and reports match
```

**Total delay: ~7.5 minutes**

## Root Cause

When SLURM kills a job externally (OOM, timeout, manual cancel), it does **not** write the `.exitcode` file that Nextflow expects. Nextflow must then:

1. **Poll `squeue`** every 20 seconds (default `pollInterval`)
2. **Wait for exit file** up to 270 seconds (default `exitStatusReadTimeoutMillis`)
3. **After timeout**, inspect work directory to confirm job is dead
4. **Finally trigger** `onTaskComplete()` event

From `.nextflow.log`:
```
Mar-27 15:17:31.640 [Task monitor] DEBUG nextflow.executor.GridTaskHandler - 
Failed to get exit status for process TaskHandler[jobId: 48635253...] 
-- exitStatusReadTimeoutMillis: 270000; delta: 280015
```

## Why This Happens

### Normal Job Completion
When a job completes normally:
```
Job exits → Nextflow wrapper writes .exitcode → Detected in ~20s
```

### External Termination (OOM/Kill)
When SLURM kills externally:
```
SLURM kills job → No .exitcode written → Nextflow waits 4.5 min → 
  Timeout → Check workdir → Finally detect
```

### Key Configuration Values

From the log:
- **pollInterval**: 20 seconds (how often Nextflow checks job status)
- **exitStatusReadTimeoutMillis**: 270,000 ms (4.5 minutes wait for .exitcode)
- **queueStatInterval**: 1 minute (how often `squeue` is called in batch)

## Solution Options

### Option 1: Reduce exitStatusReadTimeoutMillis (Recommended)

Add to `nextflow.config`:
```groovy
executor {
    queueSize = 20
    exitReadTimeout = '30s'  // Reduce from 4.5min to 30s
}
```

**Impact:**
- Reduces delay from ~7.5 min to ~50 seconds
- Still allows time for slow filesystems
- Safe for most SLURM clusters

### Option 2: Reduce pollInterval (Aggressive)

```groovy
executor {
    pollInterval = '5s'      // Reduce from 20s to 5s
    exitReadTimeout = '30s'
}
```

**Impact:**
- Reduces delay to ~35 seconds
- Increases load on SLURM scheduler (more `squeue` calls)
- Use only if cluster can handle increased polling

### Option 3: Use Custom Exit Handler (Advanced)

Implement a custom handler that monitors SLURM job state directly:
- Watch for job state changes via `scontrol`
- Trigger immediate scan when job enters `CANCELLED` or `OUT_OF_MEMORY` state
- Requires custom Nextflow executor extension

**Impact:**
- Near-instant detection (<5 seconds)
- Complex implementation
- Requires deep Nextflow internals knowledge

## Recommended Configuration

For most use cases, add this to `nextflow.config`:

```groovy
executor {
    name = 'slurm'
    queueSize = 20
    exitReadTimeout = '30s'   // Reduce timeout
    pollInterval = '10s'      // Slightly more aggressive polling
}

logScan {
    enabled = true
    scanOnFailure = true
    patterns = [
        [pattern: 'Exceeded job memory limit', name: 'Memory Limit Exceeded', exitCode: 137]
    ]
}
```

**Expected result:** Detection within ~40-50 seconds instead of 7+ minutes.

## Why Plugin Can't Fix This

The plugin hooks into `onTaskComplete()` which is only called **after** Nextflow has detected the task completion. The plugin has no visibility into:
- SLURM job scheduler state
- File system polling
- Exit code timeout logic

These are all internal to Nextflow's `GridTaskHandler` and `TaskPollingMonitor` classes.

## Additional Notes

### What the Plugin Does Well
- ✅ Immediately scans when triggered (< 1 second)
- ✅ Correctly identifies memory limit patterns
- ✅ Sets exit code 137 for retry logic

### What Nextflow Controls
- ❌ When `onTaskComplete()` is called
- ❌ Job status polling frequency
- ❌ Exit file timeout duration

### Verification

To confirm the configuration is working, check logs for:
```
DEBUG nextflow.executor.GridTaskHandler - Failed to get exit status
  -- exitStatusReadTimeoutMillis: 30000
```

Should show reduced timeout (30000 ms instead of 270000 ms).

## References

- Nextflow executor configuration: https://www.nextflow.io/docs/latest/config.html#scope-executor
- Grid executor source: `nextflow.executor.AbstractGridExecutor`
- Task monitoring: `nextflow.processor.TaskPollingMonitor`

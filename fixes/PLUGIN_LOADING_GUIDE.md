# Loading nf-crukci-support Plugin from GitHub

## The Problem

Nextflow does NOT support loading plugins directly from ZIP URLs in the `plugins {}` block of `nextflow.config`. 
The `repositories` syntax you tried is not valid Nextflow configuration.

## The Solution

You have **two options** for loading your plugin from GitHub without requiring users to set environment variables:

---

## Option 1: Using a Shell Wrapper (RECOMMENDED - Simplest for Users)

Create a wrapper script that sets the environment variable before running Nextflow:

### 1. Upload to GitHub Release

You need to upload **both** files to your v1.0.0 release:
- `nf-crukci-support-1.0.0.zip` (already uploaded)
- `nf-crukci-support-1.0.0-meta.json` (create from the file in this directory)

### 2. Create a wrapper script in your pipeline repo

**File: `run-pipeline.sh`**
```bash
#!/bin/bash
export NXF_PLUGINS_TEST_REPOSITORY="https://github.com/crukci-bioinformatics/nf-crukci-support/releases/download/1.0.0/nf-crukci-support-1.0.0-meta.json"
nextflow run "$@"
```

### 3. Update your nextflow.config

```groovy
plugins {
    id 'nf-crukci-support@1.0.0'
}

logScan {
    enabled        = true
    scanOnSuccess  = false
    scanOnFailure  = true
    maxLinesToScan = 10000
    verbose        = true
}
```

### 4. Usage

Instead of running:
```bash
nextflow run pipeline.nf -profile standard
```

Users run:
```bash
./run-pipeline.sh pipeline.nf -profile standard
```

---

## Option 2: Using a Custom Plugin Registry (ALTERNATIVE)

Host a `plugins.json` file in your GitHub repository that Nextflow can reference.

### 1. Upload plugins.json to GitHub

Upload the `plugins.json` file to your repository. You can either:
- Put it in the `main` branch at the root
- Put it in a `gh-pages` branch
- Host it as a GitHub release asset

Example URL: `https://raw.githubusercontent.com/crukci-bioinformatics/nf-crukci-support/main/plugins.json`

### 2. Create a wrapper script

**File: `run-pipeline.sh`**
```bash
#!/bin/bash
export NXF_PLUGINS_TEST_REPOSITORY="https://raw.githubusercontent.com/crukci-bioinformatics/nf-crukci-support/main/plugins.json"
nextflow run "$@"
```

### 3. Same config as Option 1

```groovy
plugins {
    id 'nf-crukci-support@1.0.0'
}
```

### 4. Advantage

This approach allows you to maintain multiple versions in one registry file:

```json
[
    {
        "id": "nf-crukci-support",
        "releases": [
            {
                "version": "1.0.0",
                "url": "...",
                "date": "...",
                "sha512sum": "..."
            },
            {
                "version": "1.1.0",
                "url": "...",
                "date": "...",
                "sha512sum": "..."
            }
        ]
    }
]
```

---

## Why This is Necessary

Nextflow's plugin system is designed around plugin registries (like the official Nextflow plugin registry). 
The configuration parser (`PluginsDsl`) ONLY accepts:

```groovy
plugins {
    id 'plugin-name@version'
}
```

It does NOT support:
- Custom repository URLs in the config
- Direct ZIP file URLs
- Any `repositories {}` block

The `NXF_PLUGINS_TEST_REPOSITORY` environment variable is the ONLY way to specify custom plugin sources 
without publishing to the official Nextflow plugin registry.

---

## What You Need to Upload to GitHub

### For Option 1 (Meta file approach):

Upload to release v1.0.0:
1. `nf-crukci-support-1.0.0.zip` ✓ (already uploaded)
2. `nf-crukci-support-1.0.0-meta.json` (new - see file in this directory)

### For Option 2 (Registry approach):

Upload to your repository (e.g., main branch):
1. `plugins.json` (see file in this directory)

AND upload to release v1.0.0:
1. `nf-crukci-support-1.0.0.zip` ✓ (already uploaded)

---

## Testing

After uploading the meta.json file to GitHub, test with:

```bash
# Test Option 1
export NXF_PLUGINS_TEST_REPOSITORY="https://github.com/crukci-bioinformatics/nf-crukci-support/releases/download/1.0.0/nf-crukci-support-1.0.0-meta.json"
nextflow run /mnt/scratchc/bioinformatics/bowers01/checkouts/nf-overresource -profile standard

# Or test Option 2 (if you host plugins.json)
export NXF_PLUGINS_TEST_REPOSITORY="https://raw.githubusercontent.com/crukci-bioinformatics/nf-crukci-support/main/plugins.json"
nextflow run /mnt/scratchc/bioinformatics/bowers01/checkouts/nf-overresource -profile standard
```

---

## Summary

**There is NO way to load custom plugins purely from nextflow.config without either:**
1. Using environment variables (NXF_PLUGINS_TEST_REPOSITORY)
2. Publishing to the official Nextflow plugin registry
3. Using development mode (NXF_PLUGINS_DEV)

**The wrapper script approach (Option 1) is the cleanest solution** that:
- Doesn't require users to manually set environment variables
- Works reliably
- Is easy to document ("run ./run-pipeline.sh instead of nextflow run")

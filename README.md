# nf-crukci-support

A Nextflow plugin that proactively monitors task log files and handles external task terminations.
It also provides some functions used throughout the CRUK-CI pipelines.

## Features

- **Proactive monitoring**: Background TaskMonitor thread detects tasks killed externally (SLURM OOM, GPU limits, etc.)
- Scans task log files while tasks are running
- Configurable regex patterns with case-sensitive/insensitive matching
- **Exit code override**: Set custom exit codes when patterns match to trigger Nextflow errorStrategy
- Automatic detection of "memory limit" patterns with exit code 137
- Configurable maximum lines to scan
- Five supporting functions.

The log scanning functionality is documented in [LogScan.md](docs/LogScan.md). The extension functions
are documented in [Functions.md](docs/Functions.md).

## Installation

1. Build the plugin:
```bash
mvn clean package
```

2. Install to Nextflow plugins directory:
```bash
mkdir -p ~/.nextflow/plugins/nf-crukci-support-1.0-SNAPSHOT
cp target/nf-crukci-support-1.0-SNAPSHOT.jar ~/.nextflow/plugins/nf-crukci-support-1.0-SNAPSHOT/
```

## Configuration

Add the plugin to your `nextflow.config`:

```groovy
plugins {
    id 'nf-crukci-support@<version>'
}
```

Further configuration can be read in [LogScan.md](docs/LogScan.md).

### Getting the Plugin

At present this plugin is not part of the set of Nextflow plugins globally available. The only way to have the Nextflow automatically download the
plugin for you is to set the `NXF_PLUGINS_TEST_REPOSITORY` environment variable to include our own `plugins.json`.

```BASH
export NXF_PLUGINS_TEST_REPOSITORY="https://github.com/crukci-bioinformatics/nextflow-plugins/raw/refs/heads/master/plugins.json,https://raw.githubusercontent.com/nextflow-io/plugins/main/plugins.json"
```

Probably best put this into your `.bashrc`. If we put this plugin into the Nextflow main plugins file this will no longer be required, but we need
to be sure it works properly before approaching the Nextflow people.

## Requirements

- Nextflow 25.04.0 or newer (tested with 25.04.4)
- Java 17

**Note**: While this plugin is built with Java 17, it does not use the Java Platform Module System (JPMS) because Nextflow itself is not modular. The plugin uses the traditional classpath mechanism for compatibility.

## Building

```bash
mvn clean package
```

## Testing

```bash
mvn test
```

## License

Developed at the Cancer Research UK Cambridge Institute.

## Authors

Richard Bowers (richard.bowers@cruk.cam.ac.uk)

With assistance from GitHub CoPilot.


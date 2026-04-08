/**
 * Nextflow plugin for scanning task log files.
 * <p>
 * This package provides a Nextflow plugin that monitors task completions
 * and scans their log files for configurable regex patterns. When specific
 * patterns are found (e.g., "Exceeded job memory limit"), the plugin can
 * trigger appropriate actions such as task retries.
 * </p>
 * <p>
 * The plugin integrates with Nextflow's PF4J plugin system and TraceObserver
 * lifecycle hooks to monitor task completions and perform log scanning.
 * </p>
 *
 */
package org.cruk.nextflow.plugin.logscan;

package org.cruk.nextflow.plugin.logscan;

import nextflow.plugin.BasePlugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main plugin class for nf-crukci-support.
 * <p>
 * This plugin scans task log files for configurable regex patterns
 * and handles specific issues like memory limit violations.
 * </p>
 *
 * @author Richard Bowers
 */
public class LogScanPlugin extends BasePlugin
{
    /**
     * Logger instance for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(LogScanPlugin.class);

    /**
     * Constructs a new LogScanPlugin.
     *
     * @param wrapper the plugin wrapper provided by PF4J
     */
    public LogScanPlugin(PluginWrapper wrapper)
    {
        super(wrapper);
    }

    /**
     * Called when the plugin starts.
     * <p>
     * Logs a message indicating the plugin has been loaded.
     * </p>
     */
    @Override
    public void start()
    {
        logger.info("nf-crukci-support plugin started");
    }

    /**
     * Called when the plugin stops.
     * <p>
     * Logs a message indicating the plugin is stopping.
     * </p>
     */
    @Override
    public void stop()
    {
        logger.info("nf-crukci-support plugin stopped");
    }
}

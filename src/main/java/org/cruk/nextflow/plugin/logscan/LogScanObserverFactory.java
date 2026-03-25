package org.cruk.nextflow.plugin.logscan;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import nextflow.Session;
import nextflow.trace.TraceObserver;
import nextflow.trace.TraceObserverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating LogScanObserver instances.
 * <p>
 * This factory is registered via PF4J's extension mechanism and
 * is called by Nextflow to create trace observers for each session.
 * </p>
 *
 * @author Richard Bowers
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
public class LogScanObserverFactory implements TraceObserverFactory
{
    /**
     * Logger instance for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(LogScanObserverFactory.class);

    /**
     * Creates a collection of TraceObservers for the given session.
     * <p>
     * Reads configuration from the 'logScan' scope in the session config
     * and creates a LogScanObserver if the plugin is enabled.
     * </p>
     *
     * @param session the Nextflow session
     * @return a collection containing the LogScanObserver, or empty collection if disabled
     */
    @Override
    @SuppressWarnings("unchecked")
    public Collection<TraceObserver> create(Session session)
    {
        // Read configuration from session
        Map<String, Object> configMap = null;
        Object logScanConfig = session.getConfig().get("logScan");

        if (logScanConfig instanceof Map)
        {
            configMap = (Map<String, Object>) logScanConfig;
        }

        // Create config object
        LogScanConfig config = new LogScanConfig(configMap);

        // Only create observer if enabled
        if (!config.isEnabled())
        {
            log.debug("LogScan plugin is disabled");
            return Collections.emptyList();
        }

        log.info("Creating LogScan observer");
        return Collections.singletonList(new LogScanObserver(session, config));
    }
}

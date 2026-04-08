package org.cruk.nextflow.plugin.logscan;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import nextflow.Session;
import nextflow.trace.TraceObserverV2;
import nextflow.trace.TraceObserverFactoryV2;
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
 */
public class LogScanObserverFactory implements TraceObserverFactoryV2
{
    /**
     * Logger instance for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(LogScanObserverFactory.class);

    /**
     * Creates a collection of TraceObservers for the given session.
     * <p>
     * Reads configuration from the 'logScan' scope in the session config
     * and creates a LogScanObserver.
     * </p>
     *
     * @param session the Nextflow session
     * @return a collection containing the LogScanObserver
     */
    @Override
    @SuppressWarnings("unchecked")
    public Collection<TraceObserverV2> create(Session session)
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

        logger.debug("Creating LogScan observer");
        return Collections.singletonList(new LogScanObserver(session, config));
    }
}

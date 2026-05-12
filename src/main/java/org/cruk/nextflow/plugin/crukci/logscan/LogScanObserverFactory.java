package org.cruk.nextflow.plugin.crukci.logscan;

import java.util.Collection;
import java.util.Collections;

import org.cruk.nextflow.plugin.crukci.CRUKCIConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nextflow.Session;
import nextflow.trace.TraceObserverFactoryV2;
import nextflow.trace.TraceObserverV2;

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
     * Reads configuration from the 'crukci' scope in the session config
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
        CRUKCIConfig config = new CRUKCIConfig(session);

        logger.debug("Creating LogScan observer");
        return Collections.singletonList(new LogScanObserver(session, config));
    }
}

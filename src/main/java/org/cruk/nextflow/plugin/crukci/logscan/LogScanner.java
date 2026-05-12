package org.cruk.nextflow.plugin.crukci.logscan;

import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import org.cruk.nextflow.plugin.crukci.CRUKCIConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scans log files for regex patterns.
 * <p>
 * Reads log files line by line and matches against configured patterns,
 * returning all matches found.
 * </p>
 *
 * @author Richard Bowers
 */
public class LogScanner
{
    /**
     * Logger instance for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(LogScanner.class);

    /**
     * Configuration for this scanner.
     */
    private final CRUKCIConfig config;

    /**
     * Represents a match found in a log file.
     */
    public static class ScanMatch
    {
        /**
         * The pattern that matched.
         */
        public final CRUKCIConfig.ScanPattern pattern;

        /**
         * The line number where the match was found (1-based).
         */
        public final int lineNumber;

        /**
         * The matched text.
         */
        public final String matchedText;

        /**
         * Constructs a new ScanMatch.
         *
         * @param pattern the pattern that matched
         * @param lineNumber the line number (1-based)
         * @param matchedText the matched text
         */
        public ScanMatch(CRUKCIConfig.ScanPattern pattern, int lineNumber, String matchedText)
        {
            this.pattern = pattern;
            this.lineNumber = lineNumber;
            this.matchedText = matchedText;
        }
    }

    /**
     * Constructs a new LogScanner.
     *
     * @param config the configuration to use
     */
    public LogScanner(CRUKCIConfig config)
    {
        this.config = config;
    }

    /**
     * Scans a log file for configured patterns.
     *
     * @param logFile the path to the log file
     * @return a list of matches found
     * @throws IOException if an I/O error occurs
     */
    public List<ScanMatch> scanLogFile(Path logFile) throws IOException
    {
        List<ScanMatch> matches = new ArrayList<>();

        if (!Files.exists(logFile))
        {
            logger.debug("Log file does not exist: {}", logFile);
            return matches;
        }

        logger.debug("Scanning log file: {}", logFile);

        try (LineNumberReader reader = new LineNumberReader(Files.newBufferedReader(logFile)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                int lineNumber = reader.getLineNumber();

                // Check if we've reached the maximum lines to scan
                if (config.maxLinesToScan > 0 && lineNumber > config.maxLinesToScan)
                {
                    logger.debug("Reached max lines to scan: {}", config.maxLinesToScan);
                    break;
                }

                // Check each pattern against this line
                for (CRUKCIConfig.ScanPattern scanPattern : config.patterns)
                {
                    Matcher matcher = scanPattern.pattern.matcher(line);
                    if (matcher.find())
                    {
                        String matchedText = matcher.group();
                        matches.add(new ScanMatch(scanPattern, lineNumber, matchedText));

                        logger.debug("Pattern '{}' matched at line {}: {}",
                            scanPattern.name, lineNumber, matchedText);
                    }
                }
            }
        }

        return matches;
    }
}

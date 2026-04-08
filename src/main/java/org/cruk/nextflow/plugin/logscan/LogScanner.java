package org.cruk.nextflow.plugin.logscan;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

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
    private final LogScanConfig config;

    /**
     * Represents a match found in a log file.
     */
    public static class ScanMatch
    {
        /**
         * The pattern that matched.
         */
        private final LogScanConfig.ScanPattern pattern;

        /**
         * The line number where the match was found (1-based).
         */
        private final int lineNumber;

        /**
         * The matched text.
         */
        private final String matchedText;

        /**
         * Constructs a new ScanMatch.
         *
         * @param pattern the pattern that matched
         * @param lineNumber the line number (1-based)
         * @param matchedText the matched text
         */
        public ScanMatch(LogScanConfig.ScanPattern pattern, int lineNumber, String matchedText)
        {
            this.pattern = pattern;
            this.lineNumber = lineNumber;
            this.matchedText = matchedText;
        }

        /**
         * Gets the pattern that matched.
         *
         * @return the scan pattern
         */
        public LogScanConfig.ScanPattern getPattern()
        {
            return pattern;
        }

        /**
         * Gets the line number where the match was found.
         *
         * @return the line number (1-based)
         */
        public int getLineNumber()
        {
            return lineNumber;
        }

        /**
         * Gets the matched text.
         *
         * @return the matched text
         */
        public String getMatchedText()
        {
            return matchedText;
        }
    }

    /**
     * Constructs a new LogScanner.
     *
     * @param config the configuration to use
     */
    public LogScanner(LogScanConfig config)
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
            if (config.isVerbose())
            {
                logger.debug("Log file does not exist: {}", logFile);
            }
            return matches;
        }

        if (config.isVerbose())
        {
            logger.debug("Scanning log file: {}", logFile);
        }

        int lineNumber = 0;
        int maxLines = config.getMaxLinesToScan();

        try (BufferedReader reader = Files.newBufferedReader(logFile))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                lineNumber++;

                // Check if we've reached the maximum lines to scan
                if (maxLines > 0 && lineNumber > maxLines)
                {
                    if (config.isVerbose())
                    {
                        logger.debug("Reached max lines to scan: {}", maxLines);
                    }
                    break;
                }

                // Check each pattern against this line
                for (LogScanConfig.ScanPattern scanPattern : config.getPatterns())
                {
                    Matcher matcher = scanPattern.getPattern().matcher(line);
                    if (matcher.find())
                    {
                        String matchedText = matcher.group();
                        matches.add(new ScanMatch(scanPattern, lineNumber, matchedText));

                        if (config.isVerbose())
                        {
                            logger.debug("Pattern '{}' matched at line {}: {}",
                                scanPattern.getName(), lineNumber, matchedText);
                        }
                    }
                }
            }
        }

        return matches;
    }
}

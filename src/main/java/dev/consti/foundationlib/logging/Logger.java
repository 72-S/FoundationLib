package dev.consti.foundationlib.logging;

import org.slf4j.LoggerFactory;

/**
 * Logger class that provides different levels of logging (info, warning, error, and debug).
 * It uses custom formatting and prints directly to System.out.
 */
public class Logger {
    private final org.slf4j.Logger logger;
    private Boolean debug;

    /**
     * Constructs a new Logger instance with a given name.
     *
     * @param name The logger's name will be set to the value of the {@param name} field.
     */
    public Logger(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Logger name cannot be null or empty");
        }
        this.logger = LoggerFactory.getLogger(name);
        this.debug = false;
    }

    /**
     * Constructs a new Logger instance with default name "Logger".
     */
    public Logger() {
        this("Logger");
    }

    /**
     * Logs an info-level message.
     *
     * @param message The message to log.
     * @param args    The arguments to replace placeholders in the message.
     */
    public void info(String message, Object... args) {
        log("INFO", message, false, args);
    }

    /**
     * Logs a warning-level message.
     *
     * @param message The message to log.
     * @param args    The arguments to replace placeholders in the message.
     */
    public void warn(String message, Object... args) {
        log("WARN", message, debug, args);
    }

    /**
     * Logs an error-level message.
     *
     * @param message The message to log.
     * @param args    The arguments to replace placeholders in the message.
     */
    public void error(String message, Object... args) {
        log("ERROR", message, debug, args);
    }

    /**
     * Logs a debug-level message. Only logs if debug mode is enabled.
     *
     * @param message The message to log.
     * @param args    The arguments to replace placeholders in the message.
     */
    public void debug(String message, Object... args) {
        if (debug) {
            log("DEBUG", message, true, args);
        }
    }

    /**
     * Formats and logs a message with a specific level.
     *
     * @param level    The log level (e.g., INFO, WARN, ERROR, DEBUG).
     * @param message  The message to log.
     * @param extended Whether to include extended information like caller details.
     * @param args     The arguments to replace placeholders in the message.
     */
    private void log(String level, String message, boolean extended, Object... args) {
        String formattedMessage = getString(message, extended);

        switch (level) {
            case "INFO":
                logger.info(formattedMessage, args);
                break;
            case "WARN":
                logger.warn(formattedMessage, args);
                break;
            case "ERROR":
                logger.error(formattedMessage, args);
                break;
            default:
                logger.info(formattedMessage, args); // Fallback
                break;
        }
    }

    private static String getString(String message, boolean extended) {
        String formattedMessage;
        if (extended) {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            StackTraceElement caller = stackTrace[4];
            String className = caller.getClassName();
            String methodName = caller.getMethodName();

            formattedMessage = String.format(
                "(%s#%s): %s",
                className,
                methodName,
                    message
            );

        } else {
            formattedMessage = message;
        }
        return formattedMessage;
    }

    /**
     * Enables or disables debug mode for logging.
     *
     * @param debug If {@code true}, enables debug-level logging; otherwise, disables it.
     */
    public void setDebug(Boolean debug) {
        this.debug = debug;
    }

    /**
     * Gets the current debug mode status.
     *
     * @return {@code true} if debug mode is enabled; {@code false} otherwise.
     */
    public Boolean getDebug() {
        return debug;
    }
}

package dev.consti.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

/**
 * Logger class that provides different levels of logging (info, warning, error, and debug).
 * It uses java.util.logging.Logger as the underlying logging mechanism and supports
 * formatted messages with placeholders.
 */
public class Logger {
    private final java.util.logging.Logger logger;
    private Boolean debug;

    /**
     * Constructs a new Logger instance with default settings.
     *
     * @param name The logger's name will be set to the value of the {@param name} field.
     */
    public Logger(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Logger name cannot be null or empty");
        }
        this.logger = java.util.logging.Logger.getLogger(name);
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
     * @param message The message to log, using "{}" as placeholders for arguments.
     * @param args    The arguments to replace placeholders in the message.
     */
    public void info(String message, Object... args) {
        logger.log(Level.INFO, simpleFormat(message, args));
    }

    /**
     * Logs a warning-level message.
     *
     * @param message The message to log, using "{}" as placeholders for arguments.
     * @param args    The arguments to replace placeholders in the message.
     */
    public void warn(String message, Object... args) {
        logger.log(Level.WARNING, simpleFormat(message, args));
    }

    /**
     * Logs an error-level message.
     *
     * @param message The message to log, using "{}" as placeholders for arguments.
     * @param args    The arguments to replace placeholders in the message.
     */
    public void error(String message, Object... args) {
        logger.log(Level.SEVERE, simpleFormat(message, args));
    }

    /**
     * Logs a debug-level message. Only logs if debug mode is enabled.
     *
     * @param message The message to log, using "{}" as placeholders for arguments.
     * @param args    The arguments to replace placeholders in the message.
     */
    public void debug(String message, Object... args) {
        if (debug) {
            logger.log(Level.INFO, debugFormat(message, args));
        }
    }

    /**
     * Formats a debug message with additional details (timestamp, class, method).
     *
     * @param message The debug message to log.
     * @param args    The arguments to replace placeholders in the message.
     * @return The formatted debug message.
     */
    private String debugFormat(String message, Object... args) {
        // Retrieve caller details
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StackTraceElement caller = stackTrace[3]; // Adjust if method nesting changes
        String className = caller.getClassName();
        String methodName = caller.getMethodName();

        // Format timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        // Construct the debug message
        String formattedMessage = String.format(
            "[%s DEBUG]: [%s]-(%s#%s) %s",
            timestamp,
            logger.getName(),
            className,
            methodName,
            message
        );

        // Replace "{}" with "%s" for placeholders
        formattedMessage = formattedMessage.replace("{}", "%s");
        return String.format(formattedMessage, args);
    }

    /**
     * Formats a simple log message (INFO, WARN, ERROR) with placeholders.
     *
     * @param message The message to log.
     * @param args    The arguments to replace placeholders in the message.
     * @return The formatted message.
     */
    private String simpleFormat(String message, Object... args) {
        String formattedMessage = message.replace("{}", "%s");
        return String.format(formattedMessage, args);
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

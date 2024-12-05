package dev.consti.foundationlib.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logger class that provides different levels of logging (info, warning, error, and debug).
 * It uses custom formatting and prints directly to System.out.
 */
public class Logger {
    private final String name;
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
        this.name = name;
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
        log("INFO", message, false, args);
    }

    /**
     * Logs a warning-level message.
     *
     * @param message The message to log, using "{}" as placeholders for arguments.
     * @param args    The arguments to replace placeholders in the message.
     */
    public void warn(String message, Object... args) {
        log("WARN", message, false, args);
    }

    /**
     * Logs an error-level message.
     *
     * @param message The message to log, using "{}" as placeholders for arguments.
     * @param args    The arguments to replace placeholders in the message.
     */
    public void error(String message, Object... args) {
        log("ERROR", message, false, args);
    }

    /**
     * Logs a debug-level message. Only logs if debug mode is enabled.
     *
     * @param message The message to log, using "{}" as placeholders for arguments.
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
     * @param level      The log level (e.g., INFO, WARN, ERROR, DEBUG).
     * @param message    The message to log.
     * @param extended   Whether to include extended information like caller details.
     * @param args       The arguments to replace placeholders in the message.
     */
    private void log(String level, String message, boolean extended, Object... args) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        String formattedMessage;
        if (extended) {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            StackTraceElement caller = stackTrace[3]; 
            String className = caller.getClassName();
            String methodName = caller.getMethodName();

            formattedMessage = String.format(
                "[%s %s]: [%s] (%s#%s): %s",
                timestamp,
                level,
                name,
                className,
                methodName,
                message.replace("{}", "%s")
            );
        } else {
            formattedMessage = String.format(
                "[%s %s]: [%s] %s",
                timestamp,
                level,
                name,
                message.replace("{}", "%s")
            );
        }
        System.out.println(String.format(formattedMessage, args));
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

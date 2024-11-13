package dev.consti.logging;

import java.util.logging.Level;

/**
 * Logger class that provides different levels of logging (info, warning, error, and debug).
 * It uses java.util.logging.Logger as the underlying logging mechanism and supports
 * formatted messages with placeholders.
 */
public class Logger {
    private final java.util.logging.Logger logger;
    private Boolean debug;
    private String name;

    /**
     * Constructs a new Logger instance with default settings.
     * The logger's name will be set to the value of the {@code name} field.
     */
    public Logger() {
        this.logger = java.util.logging.Logger.getLogger(name);
        this.debug = false;
    }

    /**
     * Logs an info-level message.
     * 
     * @param message The message to log, using "{}" as placeholders for arguments.
     * @param args    The arguments to replace placeholders in the message.
     */
    public void info(String message, Object... args) {
        logger.log(Level.INFO, formatString(message, args));
    }

    /**
     * Logs a warning-level message.
     * 
     * @param message The message to log, using "{}" as placeholders for arguments.
     * @param args    The arguments to replace placeholders in the message.
     */
    public void warn(String message, Object... args) {
        logger.log(Level.WARNING, formatString(message, args));
    }

    /**
     * Logs an error-level message.
     * 
     * @param message The message to log, using "{}" as placeholders for arguments.
     * @param args    The arguments to replace placeholders in the message.
     */
    public void error(String message, Object... args) {
        logger.log(Level.SEVERE, formatString(message, args));
    }

    /**
     * Logs a debug-level message. Only logs if debug mode is enabled.
     * 
     * @param message The message to log, using "{}" as placeholders for arguments.
     * @param args    The arguments to replace placeholders in the message.
     */
    public void debug(String message, Object... args) {
        if (debug) {
            logger.log(Level.INFO, formatString(message, args));
        }
    }

    /**
     * Formats a message string by replacing "{}" placeholders with provided arguments.
     * 
     * @param string The message string with placeholders.
     * @param args   The arguments to replace placeholders in the message.
     * @return The formatted message string.
     */
    private String formatString(String string, Object... args) {
        String formattedString = string.replace("{}", "%s");
        return String.format(formattedString, args);
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
     * Sets the name of the logger.
     * 
     * @param name The name to set for the logger.
     */
    public void setName(String name) {
        this.name = name;
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


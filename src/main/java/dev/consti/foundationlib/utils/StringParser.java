package dev.consti.foundationlib.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing strings with placeholders and replacing them with dynamic values.
 */
public class StringParser {

    private final Map<String, String> placeholders = new HashMap<>();

    /**
     * Adds or updates a placeholder and its corresponding value in the context.
     *
     * @param placeholder the placeholder string (e.g., "%player%").
     * @param value       the value to replace the placeholder with.
     */
    public void addPlaceholder(String placeholder, String value) {
        placeholders.put(placeholder, value);
    }

    /**
     * Removes a placeholder from the context.
     *
     * @param placeholder the placeholder string to remove (e.g., "%player%").
     */
    public void removePlaceholder(String placeholder) {
        placeholders.remove(placeholder);
    }

    /**
     * Parses the given command string, replacing placeholders with their corresponding values
     * from the context and processing argument-based placeholders.
     *
     * @param command the command string containing placeholders.
     * @param args    an array of arguments to replace argument-based placeholders (e.g., %arg[0]%, %args%).
     * @return the command string with placeholders replaced by their values.
     */
    public String parsePlaceholders(String command, String[] args) {
        if (!placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String placeholder = entry.getKey();
                String value = entry.getValue();
                command = command.replace(placeholder, value != null ? value : "");
            }
        }

        // Handle %args% placeholder
        command = command.replace("%args%", args != null ? String.join(" ", args) : "");

        // Handle %arg[index]% placeholders
        if (args != null) {
            command = replaceArgPlaceholders(command, args);
        }

        return command;
    }

    /**
     * Clears all placeholders from the context.
     */
    public void clearPlaceholders() {
        placeholders.clear();
    }

    /**
     * Replaces placeholders like %arg[index]% in the command string.
     *
     * @param command the command string.
     * @param args    the array of arguments.
     * @return the command string with %arg[index]% placeholders replaced.
     */
    private String replaceArgPlaceholders(String command, String[] args) {
        Pattern pattern = Pattern.compile("%arg\\[(\\d+)]%");
        Matcher matcher = pattern.matcher(command);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            String replacement = (index >= 0 && index < args.length) ? args[index] : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Returns a static instance of {@code StringParser}, making it a reusable utility.
     */
    public static StringParser create() {
        return new StringParser();
    }
}

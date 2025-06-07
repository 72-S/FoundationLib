package dev.consti.foundationlib.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing strings with placeholders and replacing them with
 * dynamic values.
 */
public class StringParser {
    private final Map<String, String> placeholders = new HashMap<>();
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%");

    /**
     * Adds or updates a placeholder and its corresponding value in the context.
     *
     * @param placeholder the placeholder string (e.g., "%player%").
     * @param value       the value to replace the placeholder with.
     */
    public void add(String placeholder, String value) {
        placeholders.put(placeholder, value);
    }

    /**
     * Removes a placeholder from the context.
     *
     * @param placeholder the placeholder string to remove (e.g., "%player%").
     */
    public void remove(String placeholder) {
        placeholders.remove(placeholder);
    }

    /**
     * Parses the given command string, replacing placeholders with their
     * corresponding values
     * from the context and processing argument-based placeholders.
     *
     * @param command the command string containing placeholders.
     * @param args    an array of arguments to replace argument-based placeholders
     *                (e.g., %arg[0]%, %args%).
     * @return the command string with placeholders replaced by their values.
     */
    public String parse(String command, String[] args) {
        if (!placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String placeholder = entry.getKey();
                String value = entry.getValue();
                command = command.replace(placeholder, value != null ? value : "");
            }
        }

        command = command.replace("%args%", args != null ? String.join(" ", args) : "");

        if (args != null) {
            command = replaceArgs(command, args);
        }

        return command;
    }

    /**
     * Validates if all placeholders in the command can be resolved and parses them.
     * 
     * @param command the command string containing placeholders.
     * @param args    an array of arguments to replace argument-based placeholders.
     * @return a Result containing the parsed command and any unresolved
     *         placeholders.
     */
    public Result validate(String command, String[] args) {
        Set<String> unresolved = new HashSet<>();
        Set<String> found = findAll(command);

        for (String placeholder : found) {
            if (placeholder.equals("%args%")) {
                continue;
            }

            if (placeholder.matches("%arg\\[\\d+\\]%")) {
                Pattern argPattern = Pattern.compile("%arg\\[(\\d+)\\]%");
                Matcher matcher = argPattern.matcher(placeholder);
                if (matcher.find()) {
                    int index = Integer.parseInt(matcher.group(1));
                    if (args == null || index >= args.length) {
                        unresolved.add(placeholder);
                    }
                }
                continue;
            }

            if (!placeholders.containsKey(placeholder)) {
                unresolved.add(placeholder);
            }
        }

        String parsed = parse(command, args);
        return new Result(parsed, unresolved, unresolved.isEmpty());
    }

    /**
     * Finds all placeholders in the given command string.
     * 
     * @param command the command string to search.
     * @return a set of all found placeholders.
     */
    private Set<String> findAll(String command) {
        Set<String> placeholders = new HashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(command);

        while (matcher.find()) {
            placeholders.add(matcher.group(0));
        }

        return placeholders;
    }

    /**
     * Clears all placeholders from the context.
     */
    public void clear() {
        placeholders.clear();
    }

    /**
     * Replaces placeholders like %arg[index]% in the command string.
     *
     * @param command the command string.
     * @param args    the array of arguments.
     * @return the command string with %arg[index]% placeholders replaced.
     */
    private String replaceArgs(String command, String[] args) {
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
     * Returns a static instance of {@code StringParser}, making it a reusable
     * utility.
     */
    public static StringParser create() {
        return new StringParser();
    }

    /**
     * Result class for parsing operations that includes validation information.
     */
    public static class Result {
        private final String parsed;
        private final Set<String> unresolved;
        private final boolean valid;

        public Result(String parsed, Set<String> unresolved, boolean valid) {
            this.parsed = parsed;
            this.unresolved = unresolved;
            this.valid = valid;
        }

        public String getParsed() {
            return parsed;
        }

        public Set<String> getUnresolved() {
            return unresolved;
        }

        public boolean isValid() {
            return valid;
        }
    }
}

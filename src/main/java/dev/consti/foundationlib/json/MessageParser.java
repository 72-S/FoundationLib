package dev.consti.foundationlib.json;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * MessageDeBuilder is a utility class for parsing JSON messages that follow
 * a specific structure with a type, body, timestamp, and optional status.
 * It provides methods to extract information from the message in a flexible way.
 */
public class MessageParser {

    private final JSONObject jsonObject;

    /**
     * Constructs a MessageDeBuilder from a JSON string.
     *
     * @param jsonString The JSON string to parse.
     */
    public MessageParser(String jsonString) {
        jsonObject = new JSONObject(jsonString);
    }

    /**
     * Gets the type of the message.
     *
     * @return The type of the message, or null if not found.
     */
    public String getType() {
        return jsonObject.optString("type", null);
    }

    /**
     * Gets the status of the message.
     *
     * @return The status of the message, or null if not found.
     */
    public String getStatus() {
        return jsonObject.optString("status", null);
    }

    /**
     * Gets the timestamp of the message.
     *
     * @return The timestamp of the message, or null if not found.
     */
    public String getTimestamp() {
        return jsonObject.optString("timestamp", null);
    }

    /**
     * Gets the entire body as a JSONObject.
     *
     * @return The body of the message as a JSONObject, or null if not found.
     */
    public JSONObject getBody() {
        return jsonObject.optJSONObject("body");
    }

    /**
     * Gets a specific value from the body.
     *
     * @param key The key of the value to retrieve.
     * @return The value associated with the key, or null if not found.
     */
    public Object getBodyValue(String key) {
        JSONObject body = getBody();
        return body != null ? body.opt(key) : null;
    }

    /**
     * Gets a specific value from the body as a String.
     *
     * @param key The key of the value to retrieve.
     * @return The value as a String, or null if not found.
     */
    public String getBodyValueAsString(String key) {
        return (String) getBodyValue(key);
    }

    /**
     * Gets a specific value from the body as an int.
     *
     * @param key The key of the value to retrieve.
     * @return The value as an int, or 0 if not found or invalid.
     */
    public int getBodyValueAsInt(String key) {
        Object value = getBodyValue(key);
        return value instanceof Integer ? (int) value : 0;
    }

    /**
     * Gets a specific value from the body as a boolean.
     *
     * @param key The key of the value to retrieve.
     * @return The value as a boolean, or false if not found or invalid.
     */
    public boolean getBodyValueAsBoolean(String key) {
        Object value = getBodyValue(key);
        return value instanceof Boolean && (boolean) value;
    }

    /**
     * Gets a specific value from the body as a JSONArray.
     *
     * @param key The key of the value to retrieve.
     * @return The value as a JSONArray, or null if not found or invalid.
     */
    public JSONArray getBodyValueAsArray(String key) {
        Object value = getBodyValue(key);
        return value instanceof JSONArray ? (JSONArray) value : null;
    }

    /**
     * Gets a specific value from the body as a JSONObject.
     *
     * @param key The key of the value to retrieve.
     * @return The value as a JSONObject, or null if not found or invalid.
     */
    public JSONObject getBodyValueAsObject(String key) {
        Object value = getBodyValue(key);
        return value instanceof JSONObject ? (JSONObject) value : null;
    }

    /**
     * Checks if the message contains a specific key in the body.
     *
     * @param key The key to check.
     * @return True if the key exists in the body, false otherwise.
     */
    public boolean containsBodyKey(String key) {
        JSONObject body = getBody();
        return body != null && body.has(key);
    }
}


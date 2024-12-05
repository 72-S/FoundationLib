package dev.consti.foundationlib.json;
import java.time.Instant;

import org.json.JSONObject;

/**
 * MessageBuilder is a utility class for building JSON messages with
 * default and custom properties. This class is suitable for creating command,
 * system, or authentication messages that can be sent to different endpoints.
 */
public class MessageBuilder {

    private final JSONObject jsonObject;
    private final JSONObject bodyObject;

    /**
     * Constructs a MessageBuilder with the specified type.
     *
     * @param type The type of the message (e.g., "system", "command", "auth").
     */
    public MessageBuilder(String type) {
        jsonObject = new JSONObject();
        bodyObject = new JSONObject();

        jsonObject.put("type", type);
        jsonObject.put("body", bodyObject);
        jsonObject.put("timestamp", Instant.now().toString());
    }

    /**
     * Adds a key-value pair to the body of the JSON message.
     *
     * @param key   The key to add.
     * @param value The value to associate with the key.
     * @return The MessageBuilder instance for chaining.
     */
    public MessageBuilder addToBody(String key, Object value) {
        bodyObject.put(key, value);
        return this; // Return the builder to allow method chaining
    }

    /**
     * Adds a status to the JSON message.
     *
     * @param status The status to set (e.g., "success", "failure").
     * @return The MessageBuilder instance for chaining.
     */
    public MessageBuilder withStatus(String status) {
        jsonObject.put("status", status);
        return this;
    }

    /**
     * Builds and returns the final JSON object.
     *
     * @return The JSONObject representing the final message.
     */
    public JSONObject build() {
        return jsonObject;
    }

}


package dev.consti.websocket;

import java.net.URI;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import dev.consti.logging.Logger;
import dev.consti.utils.TLSUtils;

/**
 * AbstractSecureWebSocketClient provides a secure WebSocket client setup with customizable message handling.
 * Users must extend this class and implement the `onMessage` method for custom message handling.
 */
public abstract class SimpleWebSocketClient {

    private WebSocketClient client;
    private final Logger logger;
    private final String secret;

    /**
     * Constructs a new AbstractSecureWebSocketClient with the provided logger and secret key for authentication.
     *
     * @param logger A logger for logging client events and errors
     * @param secret The secret key required for client authentication
     */
    public SimpleWebSocketClient(Logger logger, String secret) {
        this.logger = logger;
        this.secret = secret;
    }

    private WebSocketClient createWebSocketClient(URI uri) {
        return new WebSocketClient(uri) {

                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    logger.info("Connected to server: {}", getURI());
                    JSONObject authMessage = new JSONObject();
                    authMessage.put("secret", secret);
                    client.send(authMessage.toString());
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    logger.info("Disconnected from server");
                }

                @Override
                public void onError(Exception ex) {
                    logger.error("An error occurred: {}", ex.getMessage());
                    if (logger.getDebug()) {
                        ex.printStackTrace();
                    }
                }
            };
    }

    /**
     * Connects to the WebSocket server at the specified address and port.
     *
     * @param address The server's hostname or IP address
     * @param port    The port number to connect to
     */
    public void connect(String address, int port) {
        try {
            client = createWebSocketClient(new URI("wss://" + address + ":" + port));
            SSLContext sslContext = TLSUtils.createClientSSLContext();
            SSLSocketFactory factory = sslContext.getSocketFactory();
            client.setSocketFactory(factory);

            client.connect();
            logger.info("Attempting to connect to server at: {}:{}", address, port);

        } catch (Exception e) {
            logger.error("Connection failed: {}", e.getMessage());
            if (logger.getDebug()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Disconnects from the WebSocket server.
     */
    public void disconnect() {
        if (client != null) {
            client.close();
            logger.info("Disconnected successfully");
        } else {
            logger.warn("Client is not connected, so no need to disconnect.");
        }
    }

    /**
     * Sends a message to the WebSocket server.
     *
     * @param message The JSON message to send to the server
     */
    public void sendMessage(JSONObject message) {
        if (client != null && client.isOpen()) {
            client.send(message.toString());
        } else {
            logger.warn("Client is not connected, so cannot send message.");
        }
    }

    /**
     * Handles messages received from the server, including authentication checks and error handling.
     * Calls the abstract `onMessage` method for further message processing.
     *
     * @param message The received message in JSON format
     */
    private void handleMessage(String message) {
        try {
            JSONObject jsonMessage = new JSONObject(message);

            String status = jsonMessage.optString("status", "unknown");
            if ("success".equals(status)) {
                logger.info("Authentication succeeded.");
            } else if ("failure".equals(status)) {
                logger.error("Authentication failed. Closing connection.");
                client.close();
            } else if ("error".equals(status)) {
                logger.warn("Received error from server: {}", jsonMessage.optString("message"));
            } else {
                onMessage(jsonMessage); // Calls the abstract method for custom message handling
            }
        } catch (JSONException e) {
            logger.error("Failed to parse message as JSON: {}", e.getMessage());
        }
    }

    /**
     * Abstract method to handle custom messages from the server.
     * Implement this method to define behavior for incoming messages.
     *
     * @param jsonMessage The received JSON message
     */
    protected abstract void onMessage(JSONObject jsonMessage);


    /**
     * Abstract method to handle custom scripts after authentification.
     */

    protected abstract void afterAuth();
}


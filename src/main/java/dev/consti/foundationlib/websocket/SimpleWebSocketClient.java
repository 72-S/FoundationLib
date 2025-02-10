package dev.consti.foundationlib.websocket;

import java.net.URI;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import dev.consti.foundationlib.json.MessageBuilder;
import dev.consti.foundationlib.json.MessageParser;
import dev.consti.foundationlib.logging.Logger;
import dev.consti.foundationlib.utils.TLSUtils;

/**
 * AbstractSecureWebSocketClient provides a secure WebSocket client setup with
 * customizable message handling.
 * Users must extend this class and implement the `onMessage` method for custom
 * message handling.
 */
public abstract class SimpleWebSocketClient {

    private WebSocketClient client;
    private final Logger logger;
    private final String secret;

    /**
     * Constructs a new AbstractSecureWebSocketClient with the provided logger and
     * secret key for authentication.
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
            public void onOpen(ServerHandshake data) {
                try {
                    logger.info("Connected to server: {}", getURI());
                    MessageBuilder builder = new MessageBuilder("auth");
                    builder.addToBody("secret", secret);
                    JSONObject authMessage = builder.build();
                    client.send(authMessage.toString());
                } catch (Exception e) {
                    throw new RuntimeException("Error during WebSocket onOpen", e);
                }
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
                logger.error("An error occurred: {}", logger.getDebug() ? ex : ex.getMessage());
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
            logger.error("Connection failed: {}", logger.getDebug() ? e : e.getMessage());
            throw new RuntimeException("Connection failed", e);
        }
    }

    /**
     * Disconnects from the WebSocket server.
     */
    public void disconnect() {
        if (client != null) {
            try {
                client.close();
                logger.info("Disconnected successfully");

            } catch (Exception e) {
                throw new RuntimeException("Failed to disconnect WebSocket client", e);
            }
        } else {
            logger.warn("Client is not connected, so no need to disconnect");
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
            logger.warn("Client is not connected, so cannot send message");
        }
    }

    /**
     * Handles messages received from the server, including authentication checks
     * and error handling.
     * Calls the abstract `onMessage` method for further message processing.
     *
     * @param message The received message in JSON format
     */
    private void handleMessage(String message) {
        try {
            MessageParser parser = new MessageParser(message);
            if (parser.getType().equals("auth")) {
                String status = parser.getStatus();

                switch (status) {
                    case "authenticated" -> {
                        logger.info("Authentication succeeded");
                        afterAuth();
                    }
                    case "unauthenticated" -> {
                        logger.error("Authentication failed");
                        client.close();
                    }
                    case "error" ->
                        logger.warn("Received error from server: {}", parser.getBodyValueAsString("message"));
                    case null, default -> logger.error("Received not a valid status");
                }

            } else {
                onMessage(message);
            }
        } catch (JSONException e) {
            logger.error("Failed to parse message: {}", logger.getDebug() ? e : e.getMessage());
        }
    }

    /**
     * Abstract method to handle custom messages from the server.
     * Implement this method to define behavior for incoming messages.
     *
     * @param message The received JSON String message
     */
    protected abstract void onMessage(String message);

    /**
     * Abstract method to handle custom scripts after authentication.
     */
    protected abstract void afterAuth();
}

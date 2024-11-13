package dev.consti.websocket;

import java.net.InetSocketAddress;
import java.nio.channels.UnresolvedAddressException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.SSLContext;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;

import dev.consti.logging.Logger;
import dev.consti.utils.TLSUtils;
/**
 * WebSocketServerBase is an abstract WebSocket server class that provides the main functionality for handling connections,
 * authentication, and broadcasting. The `onMessage` method is abstract, allowing users to define custom behavior for 
 * handling received messages.
 */
public abstract class SecureWebSocketServer {

    protected final Logger logger;
    private WebSocketServer server;
    private final Set<WebSocket> connections = Collections.synchronizedSet(new HashSet<>());
    private final String secret;

    /**
     * Constructs a new WebSocketServerBase with the provided logger and secret key for authentication.
     *
     * @param logger A logger for logging server events and errors
     * @param secret The secret key required for client authentication
     */
    public SecureWebSocketServer(Logger logger, String secret) {
        this.logger = logger;
        this.secret = secret;
    }

    /**
     * Starts the WebSocket server on the specified address and port.
     *
     * @param port    The port number to listen on
     * @param address The hostname or IP address to bind to
     */
    public void startServer(int port, String address) {
        try {
            server = new WebSocketServer(new InetSocketAddress(address, port)) {

                @Override
                public void onOpen(WebSocket conn, ClientHandshake handshake) {
                    logger.info("New connection attempt from {}", conn.getRemoteSocketAddress());
                }

                @Override
                public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                    connections.remove(conn);
                    logger.info("Connection closed: {} with reason: {}", conn.getRemoteSocketAddress(), reason);
                }

                @Override
                public void onMessage(WebSocket conn, String message) {
                    handleMessage(conn, message);
                }

                @Override
                public void onError(WebSocket conn, Exception ex) {
                    handleError(ex, address);
                }

                @Override
                public void onStart() {
                    logger.info("Server started on: {}:{}", getAddress().getHostString(), getAddress().getPort());
                }
            };

            SSLContext sslContext = TLSUtils.createServerSSLContext();
            server.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
            server.start();
            logger.info("WebSocket server initialized on: {}:{}", address, port);

        } catch (UnresolvedAddressException e) {
            logger.error("Failed to start server: Invalid hostname or address '{}'", address);
            if (logger.getDebug()) e.printStackTrace();
        } catch (Exception e) {
            logger.error("An unexpected error occurred during server startup: {}", e.getMessage());
            if (logger.getDebug()) e.printStackTrace();
        }
    }

    /**
     * Stops the WebSocket server with a specified timeout.
     *
     * @param timeout The timeout in milliseconds for stopping the server
     */
    public void stopServer(int timeout) {
        if (server != null) {
            try {
                server.stop(timeout);
                logger.info("Server stopped successfully");
            } catch (InterruptedException e) {
                logger.error("Failed to stop server gracefully: {}", e.getMessage());
                if (logger.getDebug()) e.printStackTrace();
            }
        } else {
            logger.warn("Server is not running, so no need to stop.");
        }
    }

    /**
     * Sends a message to all connected clients.
     *
     * @param message The JSON message to send to all clients
     */
    public void sendMessage(JSONObject message) {
        synchronized (connections) {
            for (WebSocket conn : connections) {
                conn.send(message.toString());
            }
        }
        logger.debug("Sent message: {}", message.toString());
    }

    /**
     * Handles messages received from clients, including authentication and broadcasting.
     * Calls the abstract `onMessage` method to allow custom handling of authenticated messages.
     *
     * @param conn    The WebSocket connection that sent the message
     * @param message The received message
     */
    protected void handleMessage(WebSocket conn, String message) {
        JSONObject jsonMessage = new JSONObject(message);
        logger.debug("Received message: {}", jsonMessage);

        if (jsonMessage.has("secret")) {
            String receivedSecret = jsonMessage.getString("secret");
            JSONObject response = new JSONObject();
            if (receivedSecret.equals(secret)) {
                logger.info("Client authenticated successfully: {}", conn.getRemoteSocketAddress());
                connections.add(conn);
                response.put("status", "success").put("message", "Authentication successful");
            } else {
                logger.warn("Client failed to authenticate: {}", conn.getRemoteSocketAddress());
                response.put("status", "failure").put("message", "Authentication failed");
                conn.send(response.toString());
                conn.close(4001, "Authentication failed");
                return;
            }
            conn.send(response.toString());
        } else if (connections.contains(conn)) {
            onMessage(conn, jsonMessage); // Customizable message handling
        } else {
            JSONObject unauthorizedResponse = new JSONObject()
                    .put("status", "error")
                    .put("message", "Unauthorized");
            logger.warn("Received message from unauthenticated client: {}", conn.getRemoteSocketAddress());
            conn.send(unauthorizedResponse.toString());
            conn.close();
        }
    }

    /**
     * Handles errors occurring on the server.
     *
     * @param ex      The exception thrown
     * @param address The server address
     */
    private void handleError(Exception ex, String address) {
        if (ex instanceof UnresolvedAddressException) {
            logger.warn("Invalid hostname or address: {}", address);
        } else {
            logger.error("An error occurred: {}", ex.getMessage());
            if (logger.getDebug()) ex.printStackTrace();
        }
    }

    /**
     * Abstract method to handle messages from authenticated clients.
     * Implement this method to define custom behavior for received messages.
     *
     * @param conn        The WebSocket connection that sent the message
     * @param jsonMessage The received JSON message
     */
    protected abstract void onMessage(WebSocket conn, JSONObject jsonMessage);

    /**
     * Broadcasts a message to all clients except the sender.
     *
     * @param message The JSON message to broadcast
     * @param client  The WebSocket connection of the sender
     */
    protected void broadcastMessage(JSONObject message, WebSocket client) {
        synchronized (connections) {
            for (WebSocket conn : connections) {
                if (conn != client) {
                    conn.send(message.toString());
                }
            }
        }
        logger.debug("Broadcasted message: {}", message.toString());
    }
}


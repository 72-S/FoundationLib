package dev.consti.foundationlib.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.UnresolvedAddressException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.SSLContext;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;

import dev.consti.foundationlib.json.MessageBuilder;
import dev.consti.foundationlib.json.MessageParser;
import dev.consti.foundationlib.logging.Logger;
import dev.consti.foundationlib.utils.TLSUtils;
/**
 * WebSocketServerBase is an abstract WebSocket server class that provides the main functionality for handling connections,
 * authentication, and broadcasting. The `onMessage` method is abstract, allowing users to define custom behavior for 
 * handling received messages.
 */
public abstract class SimpleWebSocketServer {

    private final Logger logger;
    private WebSocketServer server;
    private final Set<WebSocket> connections = Collections.synchronizedSet(new HashSet<>());
    private final Set<WebSocket> pendingAuthConnections = Collections.synchronizedSet(new HashSet<>());
    private final String secret;
    private final int authTimeoutMillis = 5000;

    /**
     * Constructs a new WebSocketServerBase with the provided logger and secret key for authentication.
     *
     * @param logger A logger for logging server events and errors
     * @param secret The secret key required for client authentication
     */
    public SimpleWebSocketServer(Logger logger, String secret) {
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
                    pendingAuthConnections.add(conn);

                    Timer authTimer = new Timer();
                    authTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            if (pendingAuthConnections.contains(conn)) {
                                logger.warn("Authentication timeout for connection: {}", conn.getRemoteSocketAddress());
                                MessageBuilder builder = new MessageBuilder("auth");
                                builder.addToBody("message", "Authentication timeout.");
                                builder.withStatus("error");
                                conn.send(builder.build().toString());
                                conn.close(4002,"Authentication timeout.");
                            }
                        }
                    }, authTimeoutMillis);
                }

                @Override
                public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                    connections.remove(conn);
                    pendingAuthConnections.remove(conn);
                    onConnectionClose(conn, code, reason);
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
    public void sendMessage(JSONObject message, WebSocket conn) {
        conn.send(message.toString());
        logger.debug("Sent message: {}", message.toString());
    }

    /**
     * Handles messages received from clients, including authentication and broadcasting.
     * Calls the abstract `onMessage` method to allow custom handling of authenticated messages.
     *
     * @param conn    The WebSocket connection that sent the message
     * @param message The received message
     */
    private void handleMessage(WebSocket conn, String message) {
        try {
            MessageParser parser = new MessageParser(message);
            if (parser.getType().equals("auth")) {
                pendingAuthConnections.remove(conn);
                String receivedSecret = parser.getBodyValueAsString("secret");
                MessageBuilder builder = new MessageBuilder("auth");
                if (receivedSecret.equals(secret)) {
                    logger.info("Client authenticated successfully: {}", conn.getRemoteSocketAddress());
                    connections.add(conn);
                    builder.withStatus("authenticated");
                    sendMessage(builder.build(), conn);
                } else  {
                    logger.warn("Client failed to authenticate: {}", conn.getRemoteSocketAddress());
                    builder.withStatus("unauthenticated");
                    sendMessage(builder.build(), conn);
                    conn.close(4001, "Authentication failed");
                }
            } else if (connections.contains(conn)){
                onMessage(conn, message);
            }
        } catch (JSONException e) {
            logger.error("Failed to parse message as JSON: {}", e.getMessage());
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
        } else if (ex instanceof IOException && ex.getMessage().equals("Connection reset by peer")) {
            logger.debug("Connection reset by peer: {}", address);
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
     * @param message The received JSON String message
     */
    protected abstract void onMessage(WebSocket conn, String message);

    /**
     * Abstract method that gets called on connection close.
     * @param conn The WebSocket connection that got closed
     * @param code The disconnect code
     * @param reason The reason why the connection was closed
     */
    protected abstract void onConnectionClose(WebSocket conn, int code, String reason);

    /**
     * Broadcasts a message to all clients except the sender.
     *
     * @param message The JSON message to broadcast
     * @param client  The WebSocket connection of the sender
     */
    public void broadcastMessage(JSONObject message, WebSocket client) {
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

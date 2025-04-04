package dev.consti.foundationlib.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
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
 * WebSocketServerBase is an abstract WebSocket server class that provides the
 * main functionality for handling connections,
 * authentication, and broadcasting. The `onMessage` method is abstract,
 * allowing users to define custom behavior for
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
     * Constructs a new WebSocketServerBase with the provided logger and secret key
     * for authentication.
     *
     * @param logger A logger for logging server events and errors
     * @param secret The secret key required for client authentication
     */
    public SimpleWebSocketServer(Logger logger, String secret) {
        this.logger = logger;
        this.secret = secret;
    }

    /**
     * Checks if the WebSocket server is running.
     *
     * @return true if the server is running, false otherwise
     */
    public boolean isRunning() {
        if (server == null) {
            return false;
        }

        try {
            InetSocketAddress address = server.getAddress();
            if (address != null && isPortInUse(address.getPort())) {
                return true;
            }
        } catch (Exception e) {
            logger.error("Error checking server status: {}", logger.getDebug() ? e : e.getMessage());
        }
        return false;
    }

    /**
     * Checks if a port is in use.
     *
     * @param port The port to check
     * @return true if the port is in use, false otherwise
     */
    private boolean isPortInUse(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * Starts the WebSocket server on the specified address and port.
     *
     * @param port    The port number to listen on
     * @param address The hostname or IP address to bind to
     */
    public void startServer(int port, String address, String SAN) {
        if (isRunning()) {
            throw new RuntimeException("WebSocket server is already running on " + address + ":" + port);
        }
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
                                conn.close(4002, "Authentication timeout.");
                            }
                        }
                    }, authTimeoutMillis);
                }

                @Override
                public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                    connections.remove(conn);
                    pendingAuthConnections.remove(conn);
                    onConnectionClose(conn, code, reason);
                    logger.info("Connection '{}' closed with reason: {}", conn.getRemoteSocketAddress(), reason);
                }

                @Override
                public void onMessage(WebSocket conn, String message) {
                    handleMessage(conn, message);
                }

                @Override
                public void onError(WebSocket conn, Exception ex) {
                    logger.error("An error occurred: {}", logger.getDebug() ? ex : ex.getMessage());
                }

                @Override
                public void onStart() {
                    logger.info("WebSocket server started on: {}:{}", getAddress().getHostString(),
                            getAddress().getPort());
                }
            };

            SSLContext sslContext = TLSUtils.createServerSSLContext(SAN);
            if (sslContext == null) {
                throw new RuntimeException("Failed to initialize SSL context");
            }
            server.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
            server.start();
            logger.info("WebSocket server initialized");

        } catch (Exception e) {
            throw new RuntimeException("Error starting WebSocket server", e);
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
                logger.debug("Closing all client connections...");
                for (WebSocket conn : server.getConnections()) {
                    conn.close(1001, "Server shutdown");
                }

                server.stop(timeout);
                server = null;
                logger.info("WebSocket server stopped successfully");
            } catch (InterruptedException e) {
                throw new RuntimeException("Failed to stop WebSocket server gracefully", e);
            }
        } else {
            logger.warn("WebSocket server is not running.");
        }
    }

    /**
     * Sends a message to all connected clients.
     *
     * @param message The JSON message to send to all clients
     */
    public void sendMessage(JSONObject message, WebSocket conn) {
        conn.send(message.toString());
    }

    /**
     * Handles messages received from clients, including authentication and
     * broadcasting.
     * Calls the abstract `onMessage` method to allow custom handling of
     * authenticated messages.
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
                } else {
                    logger.warn("Client failed to authenticate: {}", conn.getRemoteSocketAddress());
                    builder.withStatus("unauthenticated");
                    sendMessage(builder.build(), conn);
                    conn.close(4001, "Authentication failed");
                }
            } else if (connections.contains(conn)) {
                onMessage(conn, message);
            }
        } catch (JSONException e) {
            logger.error("Failed to parse message: {}", logger.getDebug() ? e : e.getMessage());
        }
    }

    /**
     * Abstract method to handle messages from authenticated clients.
     * Implement this method to define custom behavior for received messages.
     *
     * @param conn    The WebSocket connection that sent the message
     * @param message The received JSON String message
     */
    protected abstract void onMessage(WebSocket conn, String message);

    /**
     * Abstract method that gets called on connection close.
     * 
     * @param conn   The WebSocket connection that got closed
     * @param code   The disconnect code
     * @param reason The reason why the connection was closed
     */
    protected abstract void onConnectionClose(WebSocket conn, int code, String reason);

    /**
     * Broadcasts a message to all clients except the sender.
     *
     * @param message The JSON message to broadcast
     * @param client  The WebSocket connection of the sender
     */
    public void broadcastClientMessage(JSONObject message, WebSocket client) {
        synchronized (connections) {
            for (WebSocket conn : connections) {
                if (conn != client) {
                    conn.send(message.toString());
                }
            }
        }
        logger.debug("Broadcast client message");
    }

    /**
     * Broadcasts a message to all connected clients from the server
     *
     * @param message The JSON message to broadcast
     */
    public void broadcastServerMessage(JSONObject message) {
        synchronized (connections) {
            for (WebSocket conn : connections) {
                conn.send(message.toString());
            }
        }
        logger.debug("Broadcast client message");
    }
}

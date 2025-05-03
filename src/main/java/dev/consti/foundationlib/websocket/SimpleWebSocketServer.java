package dev.consti.foundationlib.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.json.JSONException;
import org.json.JSONObject;

import dev.consti.foundationlib.json.MessageBuilder;
import dev.consti.foundationlib.json.MessageParser;
import dev.consti.foundationlib.logging.Logger;
import dev.consti.foundationlib.utils.TLSUtils;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslHandler;

/**
 * WebSocketServerBase is an abstract WebSocket server class that provides the
 * main functionality for handling connections,
 * authentication, and broadcasting. The `onMessage` method is abstract,
 * allowing users to define custom behavior for
 * handling received messages.
 */
public abstract class SimpleWebSocketServer {

    private final Logger logger;
    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private final Set<Channel> connections = Collections.synchronizedSet(new HashSet<>());
    private final Set<Channel> pendingAuthConnections = Collections.synchronizedSet(new HashSet<>());
    private final String secret;
    private final int authTimeoutMillis = 5000;
    private InetSocketAddress serverAddress;

    private final List<ChannelHandler> extraHandlers = new ArrayList<>();
    
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
        if (serverChannel == null) {
            return false;
        }

        try {
            if (serverAddress != null && isPortInUse(serverAddress.getPort())) {
                return true;
            }
        } catch (Exception e) {
            logger.error("Error checking server status: {}", logger.getDebug() ? e : e.getMessage());
        }
        return false;
    }


    /**
     * Adds external ChannelHandler
     *
     * @param handler Handler object
     */
    public void addHttpHandler(ChannelHandler handler) {
        extraHandlers.add(handler);
    }


    /**
     * Get the current connections map
     *
     * @return Returns connections set
     */
    public Set<Channel> getConnections() {
        return connections;
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
            this.serverAddress = new InetSocketAddress(address, port);
            
            final SSLContext sslContext = TLSUtils.createServerSSLContext(SAN);
            if (sslContext == null) {
                throw new RuntimeException("Failed to initialize SSL context");
            }


            
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            SSLEngine sslEngine = sslContext.createSSLEngine();
                            sslEngine.setUseClientMode(false);

                            
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new SslHandler(sslEngine));
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(65536));

                            for (ChannelHandler handler : extraHandlers) {
                                pipeline.addLast(handler);
                            }

                            pipeline.addLast(new WebSocketServerProtocolHandler("/", null, true));
                            pipeline.addLast(new WebSocketFrameHandler());
                        }
                    });
            
            ChannelFuture future = bootstrap.bind(serverAddress).sync();
            serverChannel = future.channel();
            
            logger.info("WebSocket server started on: {}:{}", address, port);
            
        } catch (Exception e) {
            throw new RuntimeException("Error starting WebSocket server", e);
        }
    }    /**
     * Stops the WebSocket server with a specified timeout.
     *
     * @param timeout The timeout in milliseconds for stopping the server
     */
    public void stopServer(int timeout) {
        if (serverChannel != null) {
            try {
                logger.debug("Closing all client connections...");
                for (Channel conn : connections) {
                    conn.writeAndFlush(new CloseWebSocketFrame(1001, "Server shutdown"));
                    conn.close();
                }
                
                connections.clear();
                pendingAuthConnections.clear();
                
                serverChannel.close().sync();
                
                if (bossGroup != null) {
                    bossGroup.shutdownGracefully(0, timeout, java.util.concurrent.TimeUnit.MILLISECONDS).sync();
                }
                if (workerGroup != null) {
                    workerGroup.shutdownGracefully(0, timeout, java.util.concurrent.TimeUnit.MILLISECONDS).sync();
                }
                
                serverChannel = null;
                bossGroup = null;
                workerGroup = null;
                logger.info("WebSocket server stopped successfully");
            } catch (InterruptedException e) {
                throw new RuntimeException("Failed to stop WebSocket server gracefully", e);
            }
        } else {
            logger.warn("WebSocket server is not running.");
        }
    }

    /**
     * Sends a message to a specific client.
     *
     * @param message The JSON message to send to the client
     * @param conn The channel to send the message to
     */
    public void sendMessage(JSONObject message, Channel conn) {
        conn.writeAndFlush(new TextWebSocketFrame(message.toString()));
    }

    /**
     * Handles messages received from clients, including authentication and
     * broadcasting.
     * Calls the abstract `onMessage` method to allow custom handling of
     * authenticated messages.
     *
     * @param ctx    The ChannelHandlerContext of the connection
     * @param message The received message
     */
    private void handleMessage(ChannelHandlerContext ctx, String message) {
        Channel conn = ctx.channel();
        try {
            MessageParser parser = new MessageParser(message);
            if (parser.getType().equals("auth")) {
                pendingAuthConnections.remove(conn);
                String receivedSecret = parser.getBodyValueAsString("secret");
                MessageBuilder builder = new MessageBuilder("auth");
                if (receivedSecret.equals(secret)) {
                    logger.info("Client authenticated successfully: {}", conn.remoteAddress());
                    connections.add(conn);
                    builder.withStatus("authenticated");
                    sendMessage(builder.build(), conn);
                } else {
                    logger.warn("Client failed to authenticate: {}", conn.remoteAddress());
                    builder.withStatus("unauthenticated");
                    sendMessage(builder.build(), conn);
                    conn.close();
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
     * @param conn    The channel that sent the message
     * @param message The received JSON String message
     */
    protected abstract void onMessage(Channel conn, String message);

    /**
     * Abstract method that gets called on connection close.
     * 
     * @param conn   The channel that got closed
     * @param code   The disconnect code
     * @param reason The reason why the connection was closed
     */
    protected abstract void onConnectionClose(Channel conn, int code, String reason);

    /**
     * Broadcasts a message to all clients except the sender.
     *
     * @param message The JSON message to broadcast
     * @param client  The Channel of the sender
     */
    public void broadcastClientMessage(JSONObject message, Channel client) {
        synchronized (connections) {
            for (Channel conn : connections) {
                if (conn != client) {
                    sendMessage(message, conn);
                }
            }
        }
    }
    
    /**
     * Broadcasts a message to all connected clients from the server
     *
     * @param message The JSON message to broadcast
     */
    public void broadcastServerMessage(JSONObject message) {
        synchronized (connections) {
            for (Channel conn : connections) {
                sendMessage(message, conn);
            }
        }
        logger.debug("Broadcast client message");
    }
    
    /**
     * WebSocket frame handler for processing incoming WebSocket frames.
     */
    private class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            Channel conn = ctx.channel();
            pendingAuthConnections.add(conn);
            
            Timer authTimer = new Timer();
            authTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (pendingAuthConnections.contains(conn)) {
                        // logger.warn("Authentication timeout for connection: {}", conn.remoteAddress());
                        MessageBuilder builder = new MessageBuilder("auth");
                        builder.addToBody("message", "Authentication timeout.");
                        builder.withStatus("error");
                        conn.writeAndFlush(new TextWebSocketFrame(builder.build().toString()));
                        conn.close();
                    }
                }
            }, authTimeoutMillis);
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            Channel conn = ctx.channel();
            connections.remove(conn);
            pendingAuthConnections.remove(conn);
            onConnectionClose(conn, 1000, "Connection closed");
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
            if (frame instanceof TextWebSocketFrame) {
                TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
                handleMessage(ctx, textFrame.text());
            } else if (frame instanceof CloseWebSocketFrame) {
                CloseWebSocketFrame closeFrame = (CloseWebSocketFrame) frame;
                ctx.close();
                onConnectionClose(ctx.channel(), closeFrame.statusCode(), closeFrame.reasonText());
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("An error occurred: {}", logger.getDebug() ? cause : cause.getMessage());
            ctx.close();
        }
    }
}

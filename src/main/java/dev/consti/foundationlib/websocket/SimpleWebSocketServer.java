package dev.consti.foundationlib.websocket;

import dev.consti.foundationlib.json.MessageBuilder;
import dev.consti.foundationlib.logging.Logger;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public abstract class SimpleWebSocketServer {

    private final Logger logger;
    private final String secret;
    private Channel serverChannel;
    private final Set<Channel> connections = ConcurrentHashMap.newKeySet();
    private final Set<Channel> pendingAuthConnections = ConcurrentHashMap.newKeySet();
    private final int authTimeoutMillis = 5000;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public SimpleWebSocketServer(Logger logger, String secret) {
        this.logger = logger;
        this.secret = secret;
    }

    public boolean isRunning() {
        return serverChannel != null && serverChannel.isActive();
    }

    public void startServer(int port, String address, String SAN) {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            SslContext sslCtx = SslContext.newServerContext(ssc.certificate(), ssc.privateKey());

            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(sslCtx.newHandler(ch.alloc()));
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(65536));
                            p.addLast(new WebSocketServerProtocolHandler("/ws"));
                            p.addLast(new SimpleChannelInboundHandler<Object>() {

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                                    if (msg instanceof FullHttpRequest req) {
                                        if ("/ping".equals(req.uri())) {
                                            FullHttpResponse response = new DefaultFullHttpResponse(
                                                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                                                    Unpooled.copiedBuffer("pong", CharsetUtil.UTF_8));
                                            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
                                            response.headers().set(HttpHeaderNames.CONTENT_LENGTH,
                                                    response.content().readableBytes());
                                            ctx.writeAndFlush(response);
                                        } else {
                                            FullHttpResponse response = new DefaultFullHttpResponse(
                                                    HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
                                            ctx.writeAndFlush(response);
                                        }
                                    } else if (msg instanceof TextWebSocketFrame frame) {
                                        String text = frame.text();
                                        try {
                                            JSONObject json = new JSONObject(text);
                                            if ("auth".equals(json.optString("type"))) {
                                                pendingAuthConnections.add(ctx.channel());
                                                String received = json.getJSONObject("body").optString("secret");
                                                MessageBuilder builder = new MessageBuilder("auth");
                                                if (secret.equals(received)) {
                                                    pendingAuthConnections.remove(ctx.channel());
                                                    connections.add(ctx.channel());
                                                    builder.withStatus("authenticated");
                                                    sendMessage(builder.build(), ctx.channel());
                                                    logger.info("Client authenticated successfully: {}", ctx.channel().remoteAddress());
                                                } else {
                                                    builder.withStatus("unauthenticated");
                                                    sendMessage(builder.build(), ctx.channel());
                                                    ctx.close();
                                                    logger.warn("Client failed to authenticate: {}", ctx.channel().remoteAddress());
                                                }
                                            } else if (connections.contains(ctx.channel())) {
                                                onMessage(ctx.channel(), text);
                                            }
                                        } catch (Exception e) {
                                            logger.error("Failed to parse WebSocket message: {}", logger.getDebug() ? e : e.getMessage());
                                        }
                                    }
                                }

                                @Override
                                public void handlerAdded(ChannelHandlerContext ctx) {
                                    pendingAuthConnections.add(ctx.channel());

                                    new Timer().schedule(new TimerTask() {
                                        @Override
                                        public void run() {
                                            if (pendingAuthConnections.contains(ctx.channel())) {
                                                logger.warn("Authentication timeout for connection: {}", ctx.channel().remoteAddress());
                                                MessageBuilder timeoutMsg = new MessageBuilder("auth")
                                                        .addToBody("message", "Authentication timeout.")
                                                        .withStatus("error");
                                                sendMessage(timeoutMsg.build(), ctx.channel());
                                                ctx.close();
                                            }
                                        }
                                    }, authTimeoutMillis);
                                }

                                @Override
                                public void handlerRemoved(ChannelHandlerContext ctx) {
                                    connections.remove(ctx.channel());
                                    pendingAuthConnections.remove(ctx.channel());
                                    onConnectionClose(ctx.channel(), 1000, "Disconnected");
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    logger.error("Exception in WebSocket handler: {}", logger.getDebug() ? cause : cause.getMessage());
                                    ctx.close();
                                }
                            });
                        }
                    });

            ChannelFuture f = bootstrap.bind(new InetSocketAddress(address, port)).sync();
            serverChannel = f.channel();
            logger.info("WebSocket server started on: {}:{}", address, port);

        } catch (Exception e) {
            throw new RuntimeException("Failed to start WebSocket server", e);
        }
    }

    public void stopServer(int timeout) {
        try {
            for (Channel conn : connections) {
                conn.close();
            }
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            logger.info("WebSocket server stopped successfully");
        } catch (Exception e) {
            throw new RuntimeException("Failed to stop WebSocket server", e);
        }
    }

    public void sendMessage(JSONObject message, Channel conn) {
        if (conn != null && conn.isActive()) {
            conn.writeAndFlush(new TextWebSocketFrame(message.toString()));
        }
    }

    public void broadcastClientMessage(JSONObject message, Channel client) {
        for (Channel conn : connections) {
            if (conn != client) {
                conn.writeAndFlush(new TextWebSocketFrame(message.toString()));
            }
        }
        logger.debug("Broadcast client message");
    }

    public void broadcastServerMessage(JSONObject message) {
        for (Channel conn : connections) {
            conn.writeAndFlush(new TextWebSocketFrame(message.toString()));
        }
        logger.debug("Broadcast server message");
    }

    protected abstract void onMessage(Channel conn, String message);

    protected abstract void onConnectionClose(Channel conn, int code, String reason);
} 

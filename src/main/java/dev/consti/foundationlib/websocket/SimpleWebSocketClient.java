package dev.consti.foundationlib.websocket;

import dev.consti.foundationlib.json.MessageBuilder;
import dev.consti.foundationlib.json.MessageParser;
import dev.consti.foundationlib.logging.Logger;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.json.JSONObject;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class SimpleWebSocketClient {

    private final Logger logger;
    private final String secret;
    private Channel channel;
    private WebSocketClientHandshaker handshaker;
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private EventLoopGroup group;

    public SimpleWebSocketClient(Logger logger, String secret) {
        this.logger = logger;
        this.secret = secret;
    }

    public void connect(String host, int port) {
        group = new NioEventLoopGroup();
        try {
            URI uri = new URI("wss://" + host + ":" + port + "/ws");
            String scheme = uri.getScheme() == null ? "wss" : uri.getScheme();
            String hostName = uri.getHost();
            int portNum = uri.getPort();

            SslContext sslCtx = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE).build();

            handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                    uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(sslCtx.newHandler(ch.alloc(), hostName, portNum));
                            p.addLast(new HttpClientCodec());
                            p.addLast(new HttpObjectAggregator(8192));
                            p.addLast(new SimpleChannelInboundHandler<Object>() {

                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    handshaker.handshake(ctx.channel());
                                    logger.info("Attempting to connect to server at: {}:{}", hostName, portNum);
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    Channel ch = ctx.channel();
                                    if (!handshaker.isHandshakeComplete()) {
                                        handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                                        logger.info("Connected to server: {}", uri);
                                        sendAuthMessage();
                                        return;
                                    }

                                    if (msg instanceof FullHttpResponse response) {
                                        throw new IllegalStateException(
                                                "Unexpected FullHttpResponse: " + response.status());
                                    }

                                    if (msg instanceof TextWebSocketFrame frame) {
                                        handleMessage(frame.text());
                                    }
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    logger.error("An error occurred: {}", logger.getDebug() ? cause : cause.getMessage());
                                    ctx.close();
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) {
                                    authenticated.set(false);
                                }
                            });
                        }
                    });

            ChannelFuture future = b.connect(uri.getHost(), uri.getPort()).sync();
            channel = future.channel();

        } catch (Exception e) {
            throw new RuntimeException("Connection failed", e);
        }
    }

    private void sendAuthMessage() {
        MessageBuilder builder = new MessageBuilder("auth");
        builder.addToBody("secret", secret);
        sendMessage(builder.build());
    }

    public void sendMessage(JSONObject message) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(new TextWebSocketFrame(message.toString()));
        } else {
            logger.warn("Client is not connected, so cannot send message");
        }
    }

    public void disconnect() {
        if (channel != null) {
            channel.close();
            logger.info("Disconnected successfully");
        } else {
            logger.warn("Client is not connected, so no need to disconnect");
        }
    }

    private void handleMessage(String message) {
        try {
            MessageParser parser = new MessageParser(message);
            if ("auth".equals(parser.getType())) {
                switch (parser.getStatus()) {
                    case "authenticated" -> {
                        logger.info("Authentication succeeded");
                        authenticated.set(true);
                        afterAuth();
                    }
                    case "unauthenticated" -> {
                        logger.error("Authentication failed");
                        disconnect();
                    }
                    case "error" ->
                            logger.warn("Received error from server: {}", parser.getBodyValueAsString("message"));
                    default -> logger.error("Received not a valid status");
                }
            } else {
                onMessage(message);
            }
        } catch (Exception e) {
            logger.error("Failed to parse message: {}", logger.getDebug() ? e : e.getMessage());
        }
    }

    protected abstract void onMessage(String message);

    protected abstract void afterAuth();
}

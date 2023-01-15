package fr.bramsou.messaging.netty;

import fr.bramsou.messaging.netty.packet.NettyPacket;
import fr.bramsou.messaging.netty.session.NettySession;
import fr.bramsou.messaging.netty.util.TaskHandler;
import io.netty.channel.*;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.WriteTimeoutException;

import java.net.ConnectException;

public class NettyNetwork extends SimpleChannelInboundHandler<NettyPacket> implements TaskHandler {

    private final NettySession session;
    private Channel channel;
    private boolean disconnected;

    public NettyNetwork(NettySession session) {
        this.session = session;
    }

    public void sendPacket(NettyPacket packet) {
        if (this.channel == null) return;

        if (packet.hasSendingPriority()) {
            this.sendPacketInternal(packet);
        } else {
            this.executeTask(() -> this.sendPacketInternal(packet));
        }
    }

    private void sendPacketInternal(NettyPacket packet) {
        this.channel.writeAndFlush(packet).addListener(future -> {
            if (future.isSuccess()) {
                packet.write(this);
            } else {
                this.exceptionCaught(null, future.cause());
            }
        });
    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String message;
        if (cause instanceof ConnectTimeoutException || (cause instanceof ConnectException && cause.getMessage().contains("connection timed out"))) {
            message = "Connection timed out.";
        } else if (cause instanceof ReadTimeoutException) {
            message = "Read timed out.";
        } else if (cause instanceof WriteTimeoutException) {
            message = "Write timed out.";
        } else {
            message = cause.toString();
        }

        this.disconnect(message, cause);
    }

    public void disconnect(String reason) {
        this.disconnect(reason, null);
    }

    public void disconnect(String reason, Throwable cause) {
        if (this.disconnected) return;

        this.disconnected = true;

        if (this.channel != null && this.channel.isOpen()) {
            this.channel.flush().close();
        }

        if (cause != null) {
            throw new RuntimeException("Exception while disconnecting", cause);
        }
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (this.disconnected || this.channel != null) {
            ctx.channel().close();
            return;
        }

        System.out.println("ACTIVE");
        this.channel = ctx.channel();
        this.session.channelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (ctx.channel() == this.channel) {
            this.disconnect(null);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, NettyPacket packet) {
        packet.read(this);
    }

    public boolean isConnected() {
        return !this.disconnected && this.channel != null && this.channel.isOpen();
    }

    public Channel getChannel() {
        return channel;
    }
}

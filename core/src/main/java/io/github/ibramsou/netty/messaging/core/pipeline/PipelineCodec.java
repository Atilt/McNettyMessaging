package io.github.ibramsou.netty.messaging.core.pipeline;

import io.github.ibramsou.netty.messaging.api.network.Network;
import io.github.ibramsou.netty.messaging.api.packet.MessagingPacket;
import io.github.ibramsou.netty.messaging.api.packet.PacketBuffer;
import io.github.ibramsou.netty.messaging.api.packet.PacketDirection;
import io.github.ibramsou.netty.messaging.api.pipeline.PipelineHandler;
import io.github.ibramsou.netty.messaging.core.packet.MessagingPacketBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.handler.codec.DecoderException;

import java.util.List;

public class PipelineCodec extends ByteToMessageCodec<MessagingPacket> implements PipelineHandler {

    private final Network network;

    public PipelineCodec(Network network) {
        this.network = network;
    }

    @Override
    protected void encode(ChannelHandlerContext context, MessagingPacket packet, ByteBuf out) {
        if (this.network.getState() == null) throw new IllegalArgumentException("Cannot encode packet before network state is null !");

        int initial = out.writerIndex();

        try {
            int packetId = this.network.getState().getPacketId(packet);
            PacketBuffer buffer = new MessagingPacketBuffer(out, this.network);
            packet.setNetwork(this.network);
            packet.setDirection(PacketDirection.SERVER_BOUND);
            buffer.writeVarInt(packetId);
            packet.serialize(buffer);
        } catch (Throwable t) {
            out.writerIndex(initial);
            throw new RuntimeException("Unable to encode packet " + packet.getClass().getSimpleName(), t);
        }
    }

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) {
        if (this.network.getState() == null) throw new IllegalArgumentException("Cannot decode packet before network state is null !");


        if (in.readableBytes() != 0) {
            PacketBuffer buffer = new MessagingPacketBuffer(in, this.network);
            final int packetId = buffer.readVarInt();
            MessagingPacket packet = this.network.getState().constructPacketFromId(this.network, packetId, in);
            if (packet == null) {
                return;
            }
            packet.setNetwork(this.network);
            packet.setDirection(PacketDirection.CLIENT_BOUND);

            if (in.readableBytes() > 0) {
                throw new DecoderException("Cannot read packet " + packet.getClass().getSimpleName() + " start(" + in.readableBytes() + "), end(" + in.readableBytes() + ")");
            }
            out.add(packet);
        }
    }
}

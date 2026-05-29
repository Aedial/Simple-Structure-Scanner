package com.simplestructurescanner.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.simplestructurescanner.capture.StructureCaptureService;
import com.simplestructurescanner.capture.StructureCaptureSummary;


/**
 * Requests a capture preview summary for the two selected corners.
 */
public class PacketRequestStructureCapturePreview implements IMessage {

    private BlockPos firstCorner;
    private BlockPos secondCorner;

    public PacketRequestStructureCapturePreview() {
    }

    public PacketRequestStructureCapturePreview(BlockPos firstCorner, BlockPos secondCorner) {
        this.firstCorner = firstCorner;
        this.secondCorner = secondCorner;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        firstCorner = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        secondCorner = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(firstCorner.getX());
        buf.writeInt(firstCorner.getY());
        buf.writeInt(firstCorner.getZ());
        buf.writeInt(secondCorner.getX());
        buf.writeInt(secondCorner.getY());
        buf.writeInt(secondCorner.getZ());
    }

    public static class Handler implements IMessageHandler<PacketRequestStructureCapturePreview, IMessage> {
        @Override
        public IMessage onMessage(PacketRequestStructureCapturePreview message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                StructureCaptureSummary summary = StructureCaptureService.buildSummary(
                    player.getUniqueID(),
                    player.world,
                    message.firstCorner,
                    message.secondCorner
                );

                NetworkHandler.INSTANCE.sendTo(
                    new PacketStructureCapturePreview(summary, summary == null ? "chat.structurescanner.capture.empty" : ""),
                    player
                );
            });

            return null;
        }
    }
}
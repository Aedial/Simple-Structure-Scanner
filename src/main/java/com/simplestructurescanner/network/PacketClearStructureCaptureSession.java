package com.simplestructurescanner.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.simplestructurescanner.capture.StructureCaptureService;


/**
 * Clears the server-side frozen capture session after an explicit capture reset.
 */
public class PacketClearStructureCaptureSession implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<PacketClearStructureCaptureSession, IMessage> {
        @Override
        public IMessage onMessage(PacketClearStructureCaptureSession message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> StructureCaptureService.clearFrozenCapture(player.getUniqueID()));
            return null;
        }
    }
}
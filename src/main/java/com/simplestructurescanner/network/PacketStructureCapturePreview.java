package com.simplestructurescanner.network;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.simplestructurescanner.capture.StructureCaptureSummary;
import com.simplestructurescanner.client.capture.StructureCaptureClientController;


/**
 * Returns a capture preview summary to the client.
 */
public class PacketStructureCapturePreview implements IMessage {

    @Nullable
    private NBTTagCompound summaryTag;
    private String errorKey;

    public PacketStructureCapturePreview() {
    }

    public PacketStructureCapturePreview(@Nullable StructureCaptureSummary summary, String errorKey) {
        this.summaryTag = summary != null ? summary.toNBT() : null;
        this.errorKey = errorKey;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        summaryTag = ByteBufUtils.readTag(buf);
        errorKey = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, summaryTag);
        ByteBufUtils.writeUTF8String(buf, errorKey == null ? "" : errorKey);
    }

    public static class Handler implements IMessageHandler<PacketStructureCapturePreview, IMessage> {
        @Override
        public IMessage onMessage(PacketStructureCapturePreview message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> StructureCaptureClientController.handlePreviewResponse(
                message.summaryTag != null ? StructureCaptureSummary.fromNBT(message.summaryTag) : null,
                message.errorKey
                                                                                                              ));

            return null;
        }
    }
}
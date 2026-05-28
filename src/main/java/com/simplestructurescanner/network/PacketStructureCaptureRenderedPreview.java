package com.simplestructurescanner.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.client.gui.GuiStructureCapture;


/**
 * Returns an exclusion-aware structure preview to the open capture screen.
 */
public class PacketStructureCaptureRenderedPreview implements IMessage {

    @Nullable
    private byte[] structurePayload;
    @Nullable
    private NBTTagCompound structureTag;
    private String errorKey;

    public PacketStructureCaptureRenderedPreview() {
    }

    public PacketStructureCaptureRenderedPreview(@Nullable NBTTagCompound structureTag, String errorKey) {
        this.structureTag = structureTag;
        this.structurePayload = compressStructureTag(structureTag);
        this.errorKey = errorKey;

        if (structureTag != null && structurePayload == null && (this.errorKey == null || this.errorKey.isEmpty())) {
            this.errorKey = "chat.structurescanner.capture.previewFailed";
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf.readBoolean()) {
            int payloadLength = buf.readInt();
            if (payloadLength > 0) {
                structurePayload = new byte[payloadLength];
                buf.readBytes(structurePayload);
                structureTag = decompressStructureTag(structurePayload);
            }
        }

        errorKey = ByteBufUtils.readUTF8String(buf);

        if (structurePayload != null && structureTag == null && (errorKey == null || errorKey.isEmpty())) {
            errorKey = "chat.structurescanner.capture.previewFailed";
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (structurePayload == null) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeInt(structurePayload.length);
            buf.writeBytes(structurePayload);
        }

        ByteBufUtils.writeUTF8String(buf, errorKey == null ? "" : errorKey);
    }

    @Nullable
    private static byte[] compressStructureTag(@Nullable NBTTagCompound structureTag) {
        if (structureTag == null) return null;

        // ByteBufUtils.readTag is capped at 2 MiB. Sending the preview as compressed bytes keeps
        // large captures such as mansions inside the wire limit while preserving the same client parser.
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            CompressedStreamTools.writeCompressed(structureTag, stream);
            return stream.toByteArray();
        } catch (IOException exception) {
            SimpleStructureScanner.LOGGER.warn("Failed to encode rendered capture preview: {}", exception.getMessage());
            return null;
        }
    }

    @Nullable
    private static NBTTagCompound decompressStructureTag(byte[] structurePayload) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(structurePayload)) {
            return CompressedStreamTools.readCompressed(stream);
        } catch (IOException exception) {
            SimpleStructureScanner.LOGGER.warn("Failed to decode rendered capture preview: {}", exception.getMessage());
            return null;
        }
    }

    public static class Handler implements IMessageHandler<PacketStructureCaptureRenderedPreview, IMessage> {
        @Override
        public IMessage onMessage(PacketStructureCaptureRenderedPreview message, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(() -> {
                if (!(mc.currentScreen instanceof GuiStructureCapture)) return;

                ((GuiStructureCapture) mc.currentScreen).handleRenderedPreviewResponse(
                    message.structureTag,
                    message.errorKey
                );
            });

            return null;
        }
    }
}
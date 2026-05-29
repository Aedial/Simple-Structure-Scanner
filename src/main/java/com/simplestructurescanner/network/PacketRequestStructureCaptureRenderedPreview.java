package com.simplestructurescanner.network;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.simplestructurescanner.capture.StructureCaptureExclusions;
import com.simplestructurescanner.capture.StructureCaptureService;


/**
 * Requests an exclusion-aware structure preview for the capture screen.
 */
public class PacketRequestStructureCaptureRenderedPreview implements IMessage {

    private BlockPos firstCorner;
    private BlockPos secondCorner;
    @Nullable
    private NBTTagCompound exclusionTag;

    public PacketRequestStructureCaptureRenderedPreview() {
    }

    public PacketRequestStructureCaptureRenderedPreview(BlockPos firstCorner, BlockPos secondCorner,
            StructureCaptureExclusions exclusions) {
        this.firstCorner = firstCorner;
        this.secondCorner = secondCorner;
        this.exclusionTag = exclusions.toNBT();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        firstCorner = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        secondCorner = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        exclusionTag = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(firstCorner.getX());
        buf.writeInt(firstCorner.getY());
        buf.writeInt(firstCorner.getZ());
        buf.writeInt(secondCorner.getX());
        buf.writeInt(secondCorner.getY());
        buf.writeInt(secondCorner.getZ());
        ByteBufUtils.writeTag(buf, exclusionTag);
    }

    public static class Handler implements IMessageHandler<PacketRequestStructureCaptureRenderedPreview, IMessage> {
        @Override
        public IMessage onMessage(PacketRequestStructureCaptureRenderedPreview message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                StructureCaptureExclusions exclusions = StructureCaptureExclusions.fromNBT(message.exclusionTag);
                NBTTagCompound previewTag = StructureCaptureService.buildRenderedPreviewNbt(
                    player.getUniqueID(),
                    player.world,
                    message.firstCorner,
                    message.secondCorner,
                    exclusions
                );

                NetworkHandler.INSTANCE.sendTo(
                    new PacketStructureCaptureRenderedPreview(
                        previewTag,
                        previewTag == null ? "chat.structurescanner.capture.emptyAfterExclusions" : ""
                    ),
                    player
                );
            });

            return null;
        }
    }
}
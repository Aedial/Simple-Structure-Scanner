package com.simplestructurescanner.network;

import java.io.IOException;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.capture.StructureCaptureExclusions;
import com.simplestructurescanner.capture.StructureCaptureService;
import com.simplestructurescanner.capture.StructureCaptureService.SaveResult;


/**
 * Saves the capture to disk on the server with the current exclusion set applied.
 */
public class PacketRequestStructureCaptureSave implements IMessage {

    private BlockPos firstCorner;
    private BlockPos secondCorner;
    @Nullable
    private NBTTagCompound exclusionTag;

    public PacketRequestStructureCaptureSave() {
    }

    public PacketRequestStructureCaptureSave(BlockPos firstCorner, BlockPos secondCorner,
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

    public static class Handler implements IMessageHandler<PacketRequestStructureCaptureSave, IMessage> {
        @Override
        public IMessage onMessage(PacketRequestStructureCaptureSave message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                StructureCaptureExclusions exclusions = StructureCaptureExclusions.fromNBT(message.exclusionTag);

                try {
                    SaveResult result = StructureCaptureService.saveCapture(
                        player.world,
                        message.firstCorner,
                        message.secondCorner,
                        exclusions
                    );

                    if (result == null) {
                        player.sendMessage(new TextComponentTranslation("chat.structurescanner.capture.emptyAfterExclusions"));
                        return;
                    }

                    String path = result.getFile().getAbsolutePath();
                    player.sendMessage(new TextComponentTranslation("chat.structurescanner.capture.saved", path));
                } catch (IOException e) {
                    SimpleStructureScanner.LOGGER.warn("Failed to save structure capture: {}", e.getMessage());
                    player.sendMessage(new TextComponentTranslation("chat.structurescanner.capture.saveFailed"));
                    player.sendMessage(new TextComponentString(e.getMessage()));
                }
            });

            return null;
        }
    }
}
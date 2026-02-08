package com.simplestructurescanner.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.simplestructurescanner.searching.StructureSearchManager;
import com.simplestructurescanner.structure.StructureLocation;


/**
 * Packet sent from server to client with the result of a structure search.
 * Contains either batch results (if provider supports it) or a single location.
 * Client handles caching and sorting.
 */
public class PacketStructureSearchResult implements IMessage {
    private ResourceLocation structureId;
    private boolean isBatchResponse;

    // Single location response
    private boolean found;
    private BlockPos position;
    private int skipCount;
    private int total;
    private boolean yAgnostic;

    // Batch response
    private List<BlockPos> positions;
    private BlockPos playerPos;

    public PacketStructureSearchResult() {
    }

    /**
     * Single location response (provider doesn't support batch).
     */
    public PacketStructureSearchResult(ResourceLocation structureId, StructureLocation location, int skipCount) {
        this.structureId = structureId;
        this.isBatchResponse = false;
        this.found = location != null;
        this.skipCount = skipCount;

        if (location != null) {
            this.position = location.getPosition();
            this.total = location.getTotalFound();
            this.yAgnostic = location.isYAgnostic();
        } else {
            this.position = BlockPos.ORIGIN;
            this.total = 0;
            this.yAgnostic = false;
        }
    }

    /**
     * Batch response with all locations for caching.
     */
    public PacketStructureSearchResult(ResourceLocation structureId, List<BlockPos> positions, BlockPos playerPos) {
        this.structureId = structureId;
        this.isBatchResponse = true;
        this.positions = positions;
        this.playerPos = playerPos;
        this.found = !positions.isEmpty();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        structureId = new ResourceLocation(ByteBufUtils.readUTF8String(buf));
        isBatchResponse = buf.readBoolean();

        if (isBatchResponse) {
            int count = buf.readInt();
            positions = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int x = buf.readInt();
                int y = buf.readInt();
                int z = buf.readInt();
                positions.add(new BlockPos(x, y, z));
            }
            int px = buf.readInt();
            int py = buf.readInt();
            int pz = buf.readInt();
            playerPos = new BlockPos(px, py, pz);
            found = !positions.isEmpty();
        } else {
            found = buf.readBoolean();
            skipCount = buf.readInt();
            if (found) {
                int x = buf.readInt();
                int y = buf.readInt();
                int z = buf.readInt();
                position = new BlockPos(x, y, z);
                total = buf.readInt();
                yAgnostic = buf.readBoolean();
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, structureId.toString());
        buf.writeBoolean(isBatchResponse);

        if (isBatchResponse) {
            buf.writeInt(positions.size());
            for (BlockPos pos : positions) {
                buf.writeInt(pos.getX());
                buf.writeInt(pos.getY());
                buf.writeInt(pos.getZ());
            }
            buf.writeInt(playerPos.getX());
            buf.writeInt(playerPos.getY());
            buf.writeInt(playerPos.getZ());
        } else {
            buf.writeBoolean(found);
            buf.writeInt(skipCount);
            if (found) {
                buf.writeInt(position.getX());
                buf.writeInt(position.getY());
                buf.writeInt(position.getZ());
                buf.writeInt(total);
                buf.writeBoolean(yAgnostic);
            }
        }
    }

    public static class Handler implements IMessageHandler<PacketStructureSearchResult, IMessage> {
        @Override
        public IMessage onMessage(PacketStructureSearchResult message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (message.isBatchResponse) {
                    // Batch response: update cache
                    StructureSearchManager.handleBatchResponse(
                        message.structureId,
                        message.positions,
                        message.playerPos
                    );
                } else {
                    // Single response: provider doesn't support batch
                    StructureLocation location = null;
                    if (message.found) {
                        location = new StructureLocation(
                            message.position, message.skipCount, message.total, message.yAgnostic
                        );
                    }
                    StructureSearchManager.handleSingleResponse(message.structureId, location, message.skipCount);
                }
            });

            return null;
        }
    }
}

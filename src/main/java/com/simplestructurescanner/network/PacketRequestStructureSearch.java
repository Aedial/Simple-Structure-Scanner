package com.simplestructurescanner.network;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureProviderRegistry;


/**
 * Packet sent from client to server requesting a structure search.
 * The server performs the search and returns either batch results (if supported)
 * or a single result with the given skip count.
 */
public class PacketRequestStructureSearch implements IMessage {
    private ResourceLocation structureId;
    private BlockPos playerPos;
    private int skipCount;

    private static final int MAX_CACHE_RESULTS = 100;

    public PacketRequestStructureSearch() {
    }

    public PacketRequestStructureSearch(ResourceLocation structureId, BlockPos playerPos, int skipCount) {
        this.structureId = structureId;
        this.playerPos = playerPos;
        this.skipCount = skipCount;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        structureId = new ResourceLocation(ByteBufUtils.readUTF8String(buf));
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        playerPos = new BlockPos(x, y, z);
        skipCount = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, structureId.toString());
        buf.writeInt(playerPos.getX());
        buf.writeInt(playerPos.getY());
        buf.writeInt(playerPos.getZ());
        buf.writeInt(skipCount);
    }

    public static class Handler implements IMessageHandler<PacketRequestStructureSearch, IMessage> {
        @Override
        public IMessage onMessage(PacketRequestStructureSearch message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                World world = player.world;
                ResourceLocation structureId = message.structureId;
                BlockPos playerPos = message.playerPos;
                int skipCount = message.skipCount;

                // Try batch search first
                List<BlockPos> positions = StructureProviderRegistry.findAllNearby(
                    world, structureId, playerPos, MAX_CACHE_RESULTS
                );

                if (positions != null) {
                    // Batch supported, return all positions
                    NetworkHandler.INSTANCE.sendTo(
                        new PacketStructureSearchResult(structureId, positions, playerPos),
                        player
                    );
                } else {
                    // Batch not supported, return single location
                    StructureLocation location = StructureProviderRegistry.findNearest(
                        world, structureId, playerPos, skipCount
                    );

                    NetworkHandler.INSTANCE.sendTo(
                        new PacketStructureSearchResult(structureId, location, skipCount),
                        player
                    );
                }
            });

            return null;
        }
    }
}

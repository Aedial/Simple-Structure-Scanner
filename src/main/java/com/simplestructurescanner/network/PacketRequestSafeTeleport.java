package com.simplestructurescanner.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.simplestructurescanner.util.WorldUtils;


/**
 * Packet sent from client to server requesting a safe teleport.
 * The server calculates the safe Y coordinate (where block data is available)
 * and executes the teleport.
 */
public class PacketRequestSafeTeleport implements IMessage {
    private int x;
    private int z;
    private int startY;

    public PacketRequestSafeTeleport() {
    }

    public PacketRequestSafeTeleport(int x, int z, int startY) {
        this.x = x;
        this.z = z;
        this.startY = startY;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        z = buf.readInt();
        startY = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(z);
        buf.writeInt(startY);
    }

    public static class Handler implements IMessageHandler<PacketRequestSafeTeleport, IMessage> {
        @Override
        public IMessage onMessage(PacketRequestSafeTeleport message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                // Check if player has permission to teleport (op level 2)
                if (!player.canUseCommand(2, "tp")) return;

                World world = player.world;
                int x = message.x;
                int z = message.z;
                int startY = message.startY;

                // Find safe Y coordinate server-side where block data is available
                int safeY = WorldUtils.findSafeTeleportY(world, x, z, startY);

                // Fallback to startY if no safe spot found
                if (safeY < 0) safeY = startY;

                // Execute teleport
                player.connection.setPlayerLocation(x + 0.5, safeY, z + 0.5, player.rotationYaw, player.rotationPitch);
            });

            return null;
        }
    }
}

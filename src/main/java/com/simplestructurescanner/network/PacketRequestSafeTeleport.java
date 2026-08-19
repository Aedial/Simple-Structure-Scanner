package com.simplestructurescanner.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.pillar.ValidationContextManager;
import com.simplestructurescanner.util.WorldUtils;


/**
 * Packet sent from client to server requesting a safe teleport.
 * The server calculates the safe Y coordinate (where block data is available)
 * and executes the teleport.
 * <p>
 * When {@code fromStructure} is set (destination is a scanned structure with a
 * known Y), the server first force-populates the destination chunk so the
 * structure exists in the block data before the Y search runs — otherwise the
 * Y would be computed against raw undecorated terrain (structures are built
 * during population) and the player would land where the structure is about
 * to generate, often inside its blocks.
 */
public class PacketRequestSafeTeleport implements IMessage {
    private int x;
    private int z;
    private int startY;
    private boolean fromStructure;

    public PacketRequestSafeTeleport() {
    }

    public PacketRequestSafeTeleport(int x, int z, int startY, boolean fromStructure) {
        this.x = x;
        this.z = z;
        this.startY = startY;
        this.fromStructure = fromStructure;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        z = buf.readInt();
        startY = buf.readInt();
        fromStructure = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(z);
        buf.writeInt(startY);
        buf.writeBoolean(fromStructure);
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

                if (message.fromStructure) {
                    populateDestination(world, x, z);
                }

                // Structure destinations search bottom-up from the structure's
                // base; everything else uses the legacy nearest-to-Y search.
                int safeY = message.fromStructure
                        ? WorldUtils.findStructureTeleportY(world, x, z, startY)
                        : WorldUtils.findSafeTeleportY(world, x, z, startY);

                // Fallback to startY if no safe spot found
                if (safeY < 0) safeY = startY;

                // Execute teleport
                player.connection.setPlayerLocation(x + 0.5, safeY, z + 0.5, player.rotationYaw, player.rotationPitch);
            });

            return null;
        }

        /**
         * Force-generates and populates the destination chunk (plus generating
         * its 3x3 neighbors, which population reads/writes) so structures built
         * during population exist before any Y computation. This is the same
         * generation that would run when the player arrives — just earlier.
         * Fail-open: on any error, fall through with whatever state exists.
         */
        private static void populateDestination(World world, int x, int z) {
            try {
                int cx = x >> 4;
                int cz = z >> 4;
                Chunk chunk = world.getChunk(cx, cz);
                if (chunk.isTerrainPopulated()) return;

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        world.getChunk(cx + dx, cz + dz);
                    }
                }

                IChunkGenerator generator = ValidationContextManager.getGenerationChunkGenerator(world);
                if (generator == null) return;

                generator.populate(cx, cz);
                chunk.setTerrainPopulated(true);
                SimpleStructureScanner.LOGGER.debug(
                        "Populated destination chunk({},{}) before teleport", cx, cz);
            } catch (Exception e) {
                SimpleStructureScanner.LOGGER.debug(
                        "Destination pre-population failed for ({},{}) — using current terrain",
                        x, z, e);
            }
        }
    }
}

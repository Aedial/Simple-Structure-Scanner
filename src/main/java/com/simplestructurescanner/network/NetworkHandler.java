package com.simplestructurescanner.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;


/**
 * Handles network communication between client and server.
 * Structure searches are always performed server-side where the seed is available.
 */
public class NetworkHandler {
    // Network channel name must be <= 20 characters (Forge limitation)
    // MODID "simplestructurescanner" (22 chars) is too long, so we use a shorter alias
    private static final String NETWORK_CHANNEL = "SSS_network";

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(NETWORK_CHANNEL);

    private static int packetId = 0;

    public static void init() {
        // Client -> Server: Request structure search
        INSTANCE.registerMessage(
            PacketRequestStructureSearch.Handler.class,
            PacketRequestStructureSearch.class,
            packetId++,
            Side.SERVER
        );

        // Server -> Client: Return search result
        INSTANCE.registerMessage(
            PacketStructureSearchResult.Handler.class,
            PacketStructureSearchResult.class,
            packetId++,
            Side.CLIENT
        );

        // Client -> Server: Request safe teleport
        INSTANCE.registerMessage(
            PacketRequestSafeTeleport.Handler.class,
            PacketRequestSafeTeleport.class,
            packetId++,
            Side.SERVER
        );
    }
}

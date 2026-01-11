package com.simplestructurescanner.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import com.simplestructurescanner.SimpleStructureScanner;


/**
 * Handles network communication between client and server.
 * Structure searches are always performed server-side where the seed is available.
 */
public class NetworkHandler {
    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(SimpleStructureScanner.MODID);

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

package com.melkocore.ageoftransport.sublevel.binding;

import net.minecraft.server.MinecraftServer;

public class ServerLevelContext {
    private static MinecraftServer server;

    public static void setServer(MinecraftServer s) {
        server = s;
    }

    public static MinecraftServer getServer() {
        return server;
    }
}
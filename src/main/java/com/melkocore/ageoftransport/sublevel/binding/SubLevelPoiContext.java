package com.melkocore.ageoftransport.sublevel.binding;

import java.util.UUID;

public class SubLevelPoiContext {
    private static final ThreadLocal<UUID> currentVillager = new ThreadLocal<>();

    public static void setCurrentVillager(UUID uuid) {
        currentVillager.set(uuid);
    }

    public static UUID getCurrentVillager() {
        return currentVillager.get();
    }

    public static void clear() {
        currentVillager.remove();
    }
}
package com.melkocore.ageoftransport.villager;

import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public class VillagerEventHandler {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Villager villager)) return;

        // Inyectar el behavior en la actividad CORE con prioridad entre AcquirePoi (6) y GoToPotentialJobSite (7)
        villager.getBrain().addActivity(
                net.minecraft.world.entity.schedule.Activity.CORE,
                com.google.common.collect.ImmutableList.of(
                        com.mojang.datafixers.util.Pair.of(
                                6,
                                AcquireSubLevelJobSiteBehavior.create()
                        )
                )
        );
    }
}

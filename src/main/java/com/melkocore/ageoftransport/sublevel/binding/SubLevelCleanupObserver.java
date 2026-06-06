package com.melkocore.ageoftransport.sublevel.binding;

import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SubLevelCleanupObserver implements SubLevelObserver {

    private final ServerLevel level;

    public SubLevelCleanupObserver(ServerLevel level) {
        this.level = level;
    }

    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        UUID subLevelId = subLevel.getUniqueId();
        SubLevelBindingRegistry registry = SubLevelBindingRegistry.get(level);

        List<UUID> toUnbind = new ArrayList<>();
        for (var entry : registry.getAllBindings().entrySet()) {
            if (entry.getValue().subLevelId().equals(subLevelId)) {
                toUnbind.add(entry.getKey());
            }
        }

        for (UUID mobUUID : toUnbind) {
            registry.getLastKnownGlobalPos(mobUUID).ifPresent(pos -> {
                try {
                    // Solo release si el POI existe y tiene tickets
                    if (level.getPoiManager().existsAtPosition(
                            net.minecraft.world.entity.ai.village.poi.PoiTypes.FISHERMAN, pos)) {
                        level.getPoiManager().release(pos);
                    }
                    level.getPoiManager().remove(pos);
                } catch (Exception ignored) {}
            });

            registry.unbind(mobUUID);

            net.minecraft.world.entity.Entity entity = level.getEntity(mobUUID);
            if (entity instanceof Villager villager) {
                villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
                villager.setVillagerData(villager.getVillagerData()
                        .setProfession(net.minecraft.world.entity.npc.VillagerProfession.NONE));
            }
        }
    }
}
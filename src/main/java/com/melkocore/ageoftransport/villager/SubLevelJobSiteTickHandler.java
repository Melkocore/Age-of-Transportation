package com.melkocore.ageoftransport.villager;

import com.melkocore.ageoftransport.sublevel.binding.SubLevelBindingRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public class SubLevelJobSiteTickHandler {

    private static final int UPDATE_INTERVAL = 1;

    public static void onVillagerTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (villager.level().isClientSide()) return;
        if (villager.tickCount % UPDATE_INTERVAL != 0) return;

        ServerLevel level = (ServerLevel) villager.level();
        SubLevelBindingRegistry registry = SubLevelBindingRegistry.get(level);
        if (!registry.hasBinding(villager.getUUID())) return;

        Vec3 globalPos = registry.resolveGlobalPos(villager.getUUID(), level);
        if (globalPos == null) {
            registry.unbind(villager.getUUID());
            villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
            return;
        }

        processBinding(villager, level, registry, globalPos);
    }

    private static void processBinding(Villager villager, ServerLevel level,
                                       SubLevelBindingRegistry registry, Vec3 globalPos) {
        BlockPos updatedPos = BlockPos.containing(globalPos);
        GlobalPos updatedGlobalPos = GlobalPos.of(level.dimension(), updatedPos);

        // Remover POI anterior si la posición cambió
        villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(oldGlobalPos -> {
            if (!oldGlobalPos.pos().equals(updatedPos)) {
                try { level.getPoiManager().remove(oldGlobalPos.pos()); }
                catch (Exception ignored) {}
            }
        });

        registry.updateLastKnownGlobalPos(villager.getUUID(), updatedPos);

        var binding = registry.getBinding(villager.getUUID());
        if (binding == null) return;

        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) return;

        var subLevel = container.getSubLevel(binding.subLevelId());
        if (subLevel == null) return;

        var blockState = subLevel.getLevel().getBlockState(binding.localPos());

        // Bloque destruido — limpiar todo
        if (PoiTypes.forState(blockState).isEmpty()) {
            registry.getLastKnownGlobalPos(villager.getUUID()).ifPresent(oldPos -> {
                try {
                    // Solo release si el POI existe y tiene tickets
                    if (level.getPoiManager().existsAtPosition(
                            net.minecraft.world.entity.ai.village.poi.PoiTypes.FISHERMAN, oldPos)) {
                        level.getPoiManager().release(oldPos);
                    }
                    level.getPoiManager().remove(oldPos);
                } catch (Exception ignored) {}
            });
            registry.unbind(villager.getUUID());
            villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
            villager.setVillagerData(villager.getVillagerData()
                    .setProfession(VillagerProfession.NONE));
            System.out.println("[AOT] Bloque destruido, binding limpiado");
            return;
        }
        boolean jobSitePresent = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent();
        System.out.println("[AOT] PRE-CHECK tick=" + villager.tickCount
                + " jobSite=" + jobSitePresent
                + " prof=" + villager.getVillagerData().getProfession().name()
                + " poiExists=" + level.getPoiManager().existsAtPosition(
                PoiTypes.FISHERMAN, updatedPos));

        PoiTypes.forState(blockState).ifPresent(poiTypeHolder -> {
            // Registrar POI si no existe
            if (!level.getPoiManager().existsAtPosition(
                    poiTypeHolder.unwrapKey().get(), updatedPos)) {
                level.getPoiManager().remove(updatedPos);
                level.getPoiManager().add(updatedPos, poiTypeHolder);
                System.out.println("[AOT] POI registrado en " + updatedPos);
            }

            // Actualizar JOB_SITE
            villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, updatedGlobalPos);

            // Asignar profesión una sola vez
            if (villager.getVillagerData().getProfession() == VillagerProfession.NONE
                    && !registry.isProfessionAssigned(villager.getUUID())) {
                BuiltInRegistries.VILLAGER_PROFESSION.stream()
                        .filter(p -> p.heldJobSite().test(poiTypeHolder))
                        .findFirst()
                        .ifPresent(prof -> {
                            System.out.println("[AOT] Asignando profesión: " + prof.name());
                            villager.setVillagerData(
                                    villager.getVillagerData().setProfession(prof));
                            registry.markProfessionAssigned(villager.getUUID());
                        });
            }
        });

        // LOG
        System.out.println("[AOT] tick=" + villager.tickCount
                + " prof=" + villager.getVillagerData().getProfession().name()
                + " jobSite=" + villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .map(g -> g.pos().toString()).orElse("ABSENT")
                + " updatedPos=" + updatedPos);
    }
}
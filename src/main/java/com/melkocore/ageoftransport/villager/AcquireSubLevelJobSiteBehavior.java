package com.melkocore.ageoftransport.villager;

import com.melkocore.ageoftransport.sublevel.binding.BindingRole;
import com.melkocore.ageoftransport.sublevel.binding.SubLevelBinding;
import com.melkocore.ageoftransport.sublevel.binding.SubLevelBindingRegistry;
import com.melkocore.ageoftransport.sublevel.binding.SubLevelBindingUtil;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

public class AcquireSubLevelJobSiteBehavior {

    private static final int SEARCH_COOLDOWN = 40; // ticks entre búsquedas
    private static final double SEARCH_RADIUS = 48.0;

    public static BehaviorControl<Villager> create() {
        return BehaviorBuilder.create(
                context -> context.group(
                        context.absent(MemoryModuleType.JOB_SITE)
                ).apply(context, jobSiteMemory -> (level, villager, gameTime) -> {

                    // Solo aldeanos desempleados sin binding
                    if (villager.getVillagerData().getProfession() != VillagerProfession.NONE)
                        return false;

                    SubLevelBindingRegistry registry = SubLevelBindingRegistry.get(level);
                    if (registry.hasBinding(villager.getUUID())) return false;

                    // Cooldown — no buscar cada tick
                    if (gameTime % SEARCH_COOLDOWN != 0) return false;

                    // Buscar barril en sub-levels cercanos
                    SubLevelBinding binding = SubLevelBindingUtil.findBindableBlock(
                            villager, level, BindingRole.JOB_SITE,
                            state -> state.is(Blocks.BARREL),
                            SEARCH_RADIUS);

                    if (binding == null) return false;

                    // Crear binding
                    registry.bind(villager.getUUID(), binding);

                    // Resolver posición global y setear JOB_SITE
                    var globalPos = registry.resolveGlobalPos(villager.getUUID(), level);
                    if (globalPos == null) {
                        registry.unbind(villager.getUUID());
                        return false;
                    }

                    var updatedPos = net.minecraft.core.BlockPos.containing(globalPos);
                    var updatedGlobalPos = GlobalPos.of(level.dimension(), updatedPos);

                    // Registrar POI
                    var subLevelContainer = dev.ryanhcode.sable.api.sublevel.SubLevelContainer
                            .getContainer(level);
                    if (subLevelContainer != null) {
                        var subLevel = subLevelContainer.getSubLevel(binding.subLevelId());
                        if (subLevel != null) {
                            var blockState = subLevel.getLevel().getBlockState(binding.localPos());
                            PoiTypes.forState(blockState).ifPresent(poiTypeHolder -> {
                                if (!level.getPoiManager().existsAtPosition(
                                        poiTypeHolder.unwrapKey().get(), updatedPos)) {
                                    level.getPoiManager().remove(updatedPos);
                                    level.getPoiManager().add(updatedPos, poiTypeHolder);
                                }
                            });
                        }
                    }

                    villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, updatedGlobalPos);
                    registry.updateLastKnownGlobalPos(villager.getUUID(), updatedPos);

                    // Asignar profesión
                    PoiTypes.forState(
                            dev.ryanhcode.sable.api.sublevel.SubLevelContainer
                                    .getContainer(level)
                                    .getSubLevel(binding.subLevelId())
                                    .getLevel()
                                    .getBlockState(binding.localPos())
                    ).ifPresent(poiTypeHolder ->
                            BuiltInRegistries.VILLAGER_PROFESSION.stream()
                                    .filter(p -> p.heldJobSite().test(poiTypeHolder))
                                    .findFirst()
                                    .ifPresent(prof -> {
                                        villager.setVillagerData(
                                                villager.getVillagerData().setProfession(prof));
                                        registry.markProfessionAssigned(villager.getUUID());
                                    })
                    );

                    return true;
                })
        );
    }
}